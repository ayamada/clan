(ns drop.main
  (:import
    (com.badlogic.gdx Gdx Preferences Input$Keys Net
                      ApplicationListener
                      Application$ApplicationType)
    (com.badlogic.gdx.audio Music Sound)
    (com.badlogic.gdx.files FileHandle)
    (com.badlogic.gdx.graphics GL10 Camera OrthographicCamera Texture Color
                               Pixmap Pixmap$Format)
    (com.badlogic.gdx.graphics.g2d SpriteBatch BitmapFont BitmapFontCache
                                   TextureAtlas TextureAtlas$AtlasRegion
                                   TextureRegion
                                   Sprite NinePatch)
    (com.badlogic.gdx.math Vector2 Vector3 Rectangle Matrix4 MathUtils)
    (com.badlogic.gdx.utils TimeUtils Disposable)
    (java.lang.reflect Method)
    (java.util.concurrent RejectedExecutionException)
    (java.util.zip Deflater DeflaterOutputStream InflaterInputStream)
    (java.io File OutputStream ByteArrayOutputStream)
    )
  (:use
    [jp.ne.tir.clan.clanutil]
    [jp.ne.tir.clan.claninfo :as claninfo]
    [clojure.edn :only []]
    )
  )
;; this game is based on http://code.google.com/p/libgdx/wiki/SimpleApp .

(purge-code-when-release (set! *warn-on-reflection* true))

;;; ----------------------------------------------------------------
;;; *** consts ***
;(def eval-path "path/to/eval.clj")
;;(def eval-path "http://misc.tir.jp/proxy.cgi/eval.clj")
(def ^:const min-width 256)
(def ^:const min-height 256)
(def speed-level 100)
(def player-locate-y 32)
(def ^:const bgm-file "drop/bgm.ogg")
(def ^:const player-img-name "tsubo")
(def ^:const item-se-suffix ".wav")
(def ^:const item-table
  {:star {:type :score-a}
   :moon {:type :score-a}
   :fish {:type :score-b}
   :fruit {:type :score-b}
   :flower {:type :score-c}
   :rondel {:type :score-c}
   :hdd {:type :score-c}
   }) ; :star has (.findRegion ta "star"), /assets/drop/star.wav
(def item-spawn-interval 100000000000)
(def star-spawn-interval 10000000000)
(def console-update-interval-nsec 2000000000)


;;; ----------------------------------------------------------------
;;; came from util02

(defmacro eval-in-ns [a-ns form] `(binding [*ns* ~a-ns] (eval ~form)))

(defmacro do-each [targets [match-arg] & bodies]
  `(loop [left# ~targets]
     (when-not (empty? left#)
       (let [~match-arg (first left#)]
         ~@bodies
         (recur (rest left#))))))

(defn assets-file ^FileHandle [path]
  (.. Gdx files (internal (str "assets/" path))))

(defmacro get-delta [] `(* (.. Gdx graphics (getDeltaTime)) speed-level))

(defmacro clamp-num [min-n n max-n]
  `(let [min-n# ~min-n
         n# ~n
         max-n# ~max-n
         n2# (if min-n# (max min-n# n#) n#)]
     (if max-n# (min n2# max-n#) n2#)))

(defn open-url [url] (.openURI ^Net (.. Gdx app (getNet)) url))

(defn get-local-path [filename]
  (if (= (get-os-type) :android)
    (.path (.. Gdx files (local filename)))
    (if-release
      (str (.getParent (File. (System/getProperty "java.class.path")))
           (System/getProperty "file.separator")
           filename)
      filename)))

;; NB: YOU MUST CATCH EXCEPTION
(defn deflate-write-to-file [^String edn-str ^String file]
  (let [^bytes input-bytes (.getBytes edn-str "UTF-8")
        ^FileHandle fh (FileHandle. file)
        ^OutputStream o (.write fh false)
        ^Deflater d (Deflater.)
        ^DeflaterOutputStream dout (DeflaterOutputStream. o d) ]
    (.write dout input-bytes)
    (.close dout)
    (.close o)))

;; NB: YOU MUST CATCH EXCEPTION
(defn inflate-read-from-file [^String file]
  (let [^FileHandle fh (FileHandle. file)
        ^InputStream is (.read fh)
        ^InflaterInputStream iis (InflaterInputStream. is)
        ^ByteArrayOutputStream bout (ByteArrayOutputStream. 512) ]
    (loop [] (let [b (.read iis)] (when (not= b -1) (.write bout b) (recur))))
    (.close iis)
    (.close bout)
    (let [ba (.toByteArray bout), edn-str (String. ba 0 (count ba) "UTF-8")]
      edn-str)))

(defmacro with-spritebatch [batch & bodies]
  `(let [^SpriteBatch batch# ~batch] (.begin batch#) ~@bodies (.end batch#)))

;; asn = auto-sequence-number
(defmacro defasn [mname origin]
  (let [a-counter-var (gensym mname)]
    `(do
       (def ~a-counter-var (atom ~origin))
       (defmacro ~mname []
         (let [orig# @~a-counter-var]
           (swap! ~a-counter-var inc)
           orig#)))))
;; asdefs = auto-sequence def(s)
(defmacro defasdefs [& defs]
  (let [gen-asn (gensym 'defasdefs)
        expanded-defs (map
                        (fn [sym]
                          `(def ^:const ~sym (~gen-asn)))
                        defs)]
    `(do
       (defasn ~gen-asn 0)
       ~@expanded-defs)))

;;; ----------------------------------------------------------------
;;; *** AOLA2: Auto Object in LibGDX's Application #2 ***
(def ae-symbol '_aola2-entries)

(defmacro aola2-entries-init! []
  (list 'def ae-symbol '(atom nil)))
(defmacro aola2-entries-term! []
  (let [a-entries (eval ae-symbol)
        new-entries (map #(reduce
                            merge
                            {}
                            (map (fn [[k v]]
                                   (if (or (nil? v)
                                           (#{:draw-body
                                              :sense-body
                                              :update-body} k))
                                     {}
                                     {k v})) %)) @a-entries)]
    (reset! a-entries new-entries)
    (list 'def ae-symbol (list 'atom (list 'quote new-entries)))
    ))

(defn order-aola2-entries [entries]
  (loop [pre-entries '()
         normal-entries '()
         post-entries '()
         left-entries entries]
    (if (empty? left-entries)
      (reverse (concat post-entries normal-entries pre-entries))
      (let [entry (first left-entries)
            {order :order} entry
            r (rest left-entries)
            ]
        (case order
          :first (recur (cons entry pre-entries) normal-entries post-entries r)
          nil (recur pre-entries (cons entry normal-entries) post-entries r)
          :last (recur pre-entries normal-entries (cons entry post-entries) r)
          )))))

(defmacro defaola2 [a-name & keywords]
  (assert (symbol? a-name))
  (eval (list 'declare a-name))
  (let [param (apply array-map keywords)
        entry (array-map :name a-name
                         :ns *ns*
                         :order (:order param)
                         :create (let [e (eval (:create param))]
                                   (cond
                                     (nil? e) nil
                                     (ifn? e) e
                                     :else (fn [] e)))
                         :dispose (eval (:dispose param))
                         :resume (eval (:resume param))
                         :pause (eval (:pause param))
                         :resize (eval (:resize param))
                         :draw-body (:draw-body param)
                         :sense-body (:sense-body param)
                         :update-body (:update-body param)
                         )]
    (assert (zero? (count (filter (fn [[k v]]
                                    (not (#{:order :create :dispose :resume
                                            :pause :resize :draw-body
                                            :sense-body :update-body} k)))
                                  param))) keywords)
    (assert (let [t (:create entry)] (or (nil? t) (ifn? t))))
    (assert (let [t (:dispose entry)] (or (nil? t) (ifn? t))))
    (assert (let [t (:resume entry)] (or (nil? t) (ifn? t))))
    (assert (let [t (:pause entry)] (or (nil? t) (ifn? t))))
    (assert (let [t (:resize entry)] (or (nil? t) (ifn? t))))
    (assert (let [t (:draw-body entry)]
              (or (not (coll? t)) (not= 'fn* (first t)))))
    (assert (let [t (:sense-body entry)]
              (or (not (coll? t)) (not= 'fn* (first t)))))
    (assert (let [t (:update-body entry)]
              (or (not (coll? t)) (not= 'fn* (first t)))))
    (let [a-entries (eval ae-symbol)
          old-entries (remove #(= a-name (:name %)) @a-entries)
          new-entries (order-aola2-entries (concat old-entries (list entry)))
          ]
      (reset! a-entries new-entries)
      `(def ~a-name nil))))

(defmacro aola2-update! [aola-symbol new-aola]
  (assert (symbol? aola-symbol))
  `(let [entries# (deref ~ae-symbol)
         entry# (some (fn [%#] (when (= '~aola-symbol (:name %#)) %#))
                      entries#)]
     (assert entry#)
     (eval-in-ns (:ns entry#)
                 (list 'def (:name entry#) (list 'quote ~new-aola)))))

(defmacro aola2-handler-create! []
  `(do-each
     (deref ~ae-symbol)
     [entry#]
     (let [{a-name# :name handle# :create a-ns# :ns} entry#]
       (when handle#
         (eval-in-ns a-ns# (list 'def a-name# (list 'quote (handle#))))))))

(defmacro aola2-handler-dispose! []
  `(do-each
     (reverse (deref ~ae-symbol))
     [entry#]
     (let [{a-name# :name handle# :dispose} entry#]
       (when handle# (handle#)))))

(defmacro aola2-handler-resume! []
  `(do-each
     (deref ~ae-symbol)
     [entry#]
     (let [{a-name# :name handle# :resume} entry#]
       (when handle# (handle#)))))

(defmacro aola2-handler-pause! []
  `(do-each
     (reverse (deref ~ae-symbol))
     [entry#]
     (let [{a-name# :name handle# :pause} entry#]
       (when handle# (handle#)))))

(defmacro aola2-handler-resize! [w h batch camera]
  `(let [w# ~w, h# ~h, batch# ~batch, camera# ~camera]
     (.setToOrtho camera# false w# h#)
     (.update camera#)
     (.setProjectionMatrix batch# (.combined camera#))
     (do-each
       (deref ~ae-symbol)
       [entry#]
       (let [{a-name# :name handle# :resize} entry#]
         (when handle#
           ;(purge-code-when-release (prn handle#))
           (handle# w# h#))))))

(defmacro aola2-handler-render! [batch]
  (let [entries @(eval ae-symbol)
        draw-bodies (map :draw-body entries)
        sense-bodies (map :sense-body entries)
        update-bodies (map :update-body entries)
        ]
    `(do
       (with-spritebatch ~batch ~@draw-bodies)
       ~@sense-bodies
       ~@update-bodies
       nil)))


;;; ----------------------------------------------------------------
;;; nano clock
(definline get-nanodelta [^"[J" nc] `(aget ~nc 0))
(definline get-nanotime [^"[J" nc] `(aget ~nc 1))
(defmacro defnanoclock [a-name & [threshold]]
  (assert (symbol? a-name))
  (let [e-threshold (or (eval threshold) 50000000)
        name-with-meta (vary-meta a-name assoc :tag "[J")]
    (assert (number? e-threshold))
    `(defaola2 ~name-with-meta
       :create #(doto (long-array 3) (aset-long 2 ~e-threshold))
       :sense-body (let [
                         now# (TimeUtils/nanoTime)
                         ;now# (* (TimeUtils/millis) 1000000)
                         old-nanotime# (get-nanotime ~name-with-meta)
                         new-delta# (min (- now# old-nanotime#)
                                         (aget ~name-with-meta 2))]
                     (aset-long ~name-with-meta 0 new-delta#)
                     (aset-long ~name-with-meta 1 now#)))))


;;; ----------------------------------------------------------------
;;; common objects

(aola2-entries-init!)
(defnanoclock the-nc 50000000)

(defaola2 ^SpriteBatch the-batch
  :create #(SpriteBatch.)
  :dispose #(.dispose the-batch))

(defaola2 ^OrthographicCamera the-camera :create #(OrthographicCamera.))

(defaola2 ^TextureAtlas the-ta
  :create #(TextureAtlas. (assets-file "pack.atlas"))
  :dispose #(.dispose the-ta))

(defaola2 ^BitmapFont the-font
  :create #(doto (BitmapFont.)
             (.setFixedWidthGlyphs "0123456789"))
  :dispose #(.dispose the-font))

(defaola2 ^Vector2 screen-size
  :create #(Vector2. (float min-width) (float min-height))
  :resize (fn [w h] (.set screen-size (float w) (float h))))
(defaola2 ^Vector2 screen-center
  :create #(Vector2. (float min-width) (float min-height))
  :resize (fn [w h] (.set screen-center (float (/ w 2)) (float (/ h 2)))))

(defaola2 a-game-mode? :create #(atom true))
(defaola2 a-dialog-nothing? :create #(atom true))

;;; ----------------------------------------------------------------
;;; touches (for single only)

(def ^:const _old-touch-index 0)
(def ^:const _cur-touch-index 1)
(defaola2 ^Vector3 touch-pos :create #(Vector3.))
(defaola2 ^"[Z" a-touch-history
  :create #(boolean-array 2)
  :sense-body (let [input Gdx/input
                    is-touched (.isTouched input)]
                (aset-boolean a-touch-history _old-touch-index
                              (aget a-touch-history _cur-touch-index))
                (aset-boolean a-touch-history _cur-touch-index is-touched)
                (when is-touched
                  (.set touch-pos (.getX input) (.getY input) 0)
                  (.unproject the-camera touch-pos))))
(definline is-touched? [] `(aget a-touch-history _cur-touch-index))
(definline was-touched? [] `(aget a-touch-history _old-touch-index))
(definline just-touched? [] `(and (not (was-touched?)) (is-touched?)))
(definline just-released? [] `(and (was-touched?) (not (is-touched?))))

;;; ----------------------------------------------------------------
;;; save data manipulation

(defaola2 a-sound-off? :create #(atom false))
(defaola2 a-scores :create #(atom [0 0 0]))
(defn get-score [idx] (nth @a-scores idx))
(def ^:const save-file "save.dat")

(defaola2 ^Preferences pref
  :create #(do (keep-code-when-android
                 (.. Gdx app (getPreferences "drop")))))

(defn load-data! []
  (let [edn-str (or
                  (keep-code-when-desktop
                    (let [^String f (get-local-path save-file)]
                      (when (.exists (FileHandle. f))
                        (inflate-read-from-file f))))
                  (keep-code-when-android
                    (.getString pref "edn" ""))
                  "")]
    (when-not (= edn-str "")
      (try
        (let [edn-expr (clojure.edn/read-string edn-str)]
          (reset! a-sound-off? (:sound-off? edn-expr))
          (reset! a-scores (:scores edn-expr))
          )
        (catch Exception _ nil)))))
(defn save-data! []
  (let [edn-expr {:sound-off? @a-sound-off?
                  :scores @a-scores}
        edn-str (pr-str edn-expr)]
    (or
      (keep-code-when-desktop
        (deflate-write-to-file edn-str (get-local-path save-file)))
      (keep-code-when-android
        (.putString pref "edn" edn-str)
        (.flush pref))
      )))

(defaola2 _loader :create #(do (load-data!) nil))


;; ----------------------------------------------------------------
;; *** music ***
(defaola2 ^Music bgm
  :create #(doto (.. Gdx audio (newMusic (assets-file bgm-file)))
             (.setLooping true))
  :dispose #(.dispose bgm))
 
(defn update-music! []
  (if @a-sound-off? (.stop bgm) (.play bgm)))

(defn change-volume! [on-off]
  (reset! a-sound-off? (not on-off))
  (save-data!)
  (set-jingle-off-by-pref! (not on-off))
  (update-music!))


;;; ----------------------------------------------------------------
;;; *** background ***
(defaola2 ^TextureRegion star-tr
  :create #(.findRegion the-ta "white_dot")
  )
(defaola2 a-star-initial-dist
  :create #(atom 0)
  :resize #(reset! a-star-initial-dist (/ (+ %1 %2) 2))
  )

(defaola2 ^Vector2 tmp-v2
  :create #(Vector2.))

(def ^:const star-array-max 128)
(def ^:const bs-idx-x 0)
(def ^:const bs-idx-y 1)
(def ^:const bs-idx-tp-cur 2)
(def ^:const bs-idx-angle 3)
(def ^:const bs-idx-dist 4)
(def ^:const bs-idx-speed 5)
(def ^:const bs-idx-tp 6)
(def ^:const bs-idx-max 7)

;(definline distance->transparency [dist] `(Math/atan (/ ~dist 100)))
(definline distance->transparency [dist] `(MathUtils/atan2 ~dist 100))
(definline star-spawn [^floats star]
  `(let [result# ~star
         tp# (+ 0.3 (rand))
         tp-cur# (if (< 1 tp#) 1 tp#)
         angle# (rand 360)
         dist# @a-star-initial-dist
         speed# (+ 0.98 (rand 0.01))
         v# (doto tmp-v2
              (.set 1 0)
              (.setAngle angle#)
              (.mul (float dist#)))
         x# (+ (.x screen-center) (.x v#))
         y# (+ (.y screen-center) (.y v#))
         ]
     (aset-float result# bs-idx-x x#)
     (aset-float result# bs-idx-y y#)
     (aset-float result# bs-idx-tp-cur tp-cur#)
     (aset-float result# bs-idx-angle angle#)
     (aset-float result# bs-idx-dist dist#)
     (aset-float result# bs-idx-speed speed#)
     (aset-float result# bs-idx-tp tp#)
     result#))

(defaola2 a-background-star-next-spawn-nsec
  :create #(atom 0))

(def ^"[I" a-last-empty-idx (int-array 1))

(defaola2 ^"[Ljava.lang.Object;" a-background-stars
  :create #(object-array (take star-array-max
                               (repeatedly (fn [] (float-array bs-idx-max)))))
  :draw-body (do
               (dotimes [i star-array-max]
                 (let [^floats star (aget a-background-stars i)
                       x (aget star bs-idx-x)
                       y (aget star bs-idx-y)
                       tp-cur (aget star bs-idx-tp-cur)]
                   (when-not (zero? tp-cur)
                     (.setColor the-batch 1 1 1 tp-cur)
                     (.draw the-batch star-tr x y))))
               (.setColor the-batch 1 1 1 1))
  :update-body (let [interval (/ (get-nanodelta the-nc) 10000000)
                     now (get-nanotime the-nc)]
                 (aset-int a-last-empty-idx 0 -1)
                 (dotimes [i star-array-max]
                   (let [^floats star (aget a-background-stars i)
                         tp-cur (aget star bs-idx-tp-cur)]
                     (if (zero? tp-cur)
                       (aset-int a-last-empty-idx 0 i)
                       (let [angle (aget star bs-idx-angle)
                             dist (aget star bs-idx-dist)
                             speed (aget star bs-idx-speed)
                             tp (aget star bs-idx-tp)

                             ;new-dist (* dist (Math/pow speed interval)) ; It cause GC
                             speed-factor (float (/ speed-level (float 100)))
                             dist-factor (- 1 (* (- 1 speed) speed-factor))
                             new-dist (* dist dist-factor)
                             d-tp (distance->transparency dist)
                             tp-merge (* tp d-tp)
                             tp-lpf (min 1 tp-merge)

                             v (doto tmp-v2
                                 (.set 1 0)
                                 (.setAngle angle)
                                 (.mul (float new-dist)))
                             new-x (+ (.x screen-center) (.x v))
                             new-y (+ (.y screen-center) (.y v))
                             ]
                         (if (< tp-lpf 0.1)
                           (do
                             (aset-float star bs-idx-tp-cur 0)
                             (aset-int a-last-empty-idx 0 i))
                           (do
                             (aset-float star bs-idx-x new-x)
                             (aset-float star bs-idx-y new-y)
                             (aset-float star bs-idx-tp-cur tp-lpf)
                             ;(aset-float star bs-idx-angle angle)
                             (aset-float star bs-idx-dist new-dist)
                             ;(aset-float star bs-idx-speed speed)
                             ;(aset-float star bs-idx-tp tp)
                             ))))))
                 (let [last-empty-idx (aget a-last-empty-idx 0)]
                   (when (and
                           (< @a-background-star-next-spawn-nsec now)
                           (not (= -1 last-empty-idx)))
                     (reset! a-background-star-next-spawn-nsec
                             (+ now (rand (/ star-spawn-interval speed-level))))
                     (star-spawn (aget a-background-stars last-empty-idx))))
                 )
  )



;;; ----------------------------------------------------------------
;;; *** player ***

(def ^:const player-homing-level-max 16)
(defaola2 a-player-homing-level :create #(atom 0))

(declare a-player-x-max update-player-locate-x!)
(defaola2 ^Rectangle player-hit-rect :create #(Rectangle.))
(defaola2 ^Sprite player-sprite
  :create #(let [sprite (.createSprite the-ta player-img-name)
                 x 0
                 y player-locate-y
                 ]
             (.setPosition sprite x y)
             (.set player-hit-rect
                   (float (+ x (/ (.getWidth sprite) 2))) ; x
                   (float (+ y (.getHeight sprite))) ; y
                   (float 1) ; width
                   (float 1)) ; height
             sprite)
  :draw-body (when @a-game-mode? (.draw player-sprite the-batch))
  :update-body (when (and @a-game-mode? @a-dialog-nothing?)
                 ;; move by touch
                 (when (is-touched?)
                   (when (just-touched?)
                     (reset! a-player-homing-level player-homing-level-max))
                   (update-player-locate-x!
                     (let [x (- (.x touch-pos) (/ (.getWidth player-sprite) 2))
                           lv @a-player-homing-level]
                       (if (zero? lv)
                         x
                         (let [old-x (.getX player-sprite)
                               dist (- x old-x)
                               move-dist (* dist (/ lv))
                               new-x (+ old-x move-dist)]
                           (if (or (< old-x new-x x)
                                   (< x new-x old-x))
                             (do (reset! a-player-homing-level (dec lv)) new-x)
                             (do (reset! a-player-homing-level 0) x)))))))
                 ;; move by keyboard
                 (let [l (.. Gdx input (isKeyPressed Input$Keys/LEFT))
                       r (.. Gdx input (isKeyPressed Input$Keys/RIGHT))]
                   (when (or (and l (not r)) (and (not l) r)) ; (xor l r)
                     (update-player-locate-x!
                       ((if r + -)
                          (.getX player-sprite)
                          (/ (get-nanodelta the-nc) 10000000))))))
  )

(definline clamp-player-locate-x [x]
  `(max 0 (min @a-player-x-max ~x)))

(definline update-player-locate-x! [x]
  `(let [x# (clamp-player-locate-x ~x)]
      (set! (. player-hit-rect x) (+ x# (/ (.getWidth player-sprite) 2)))
      (.setX player-sprite x#)))

(defaola2 a-player-x-max
  :create #(atom 0)
  :resize (fn [w h]
            (reset! a-player-x-max
                    (- (.x screen-size) (.getWidth player-sprite)))
            (update-player-locate-x! (.getX player-sprite))))



;;; ----------------------------------------------------------------
;;; *** items ***
(defaola2 items-tr-table
  :create #(apply merge (map
                          (fn [[k desc]]
                            {k (.findRegion the-ta (name k))})
                          item-table)))
(defaola2 items-sprite-table
  :create #(apply merge (map
                          (fn [[k ^TextureRegion tr]]
                            {k (Sprite. tr)})
                          items-tr-table)))
(defaola2 items-se-table
  :create #(apply merge (map
                          (fn [[k desc]]
                            {k (.. Gdx audio (newSound
                                               (assets-file
                                                 (str "drop/"
                                                      (name k)
                                                      item-se-suffix))))})
                          item-table)))
(defaola2 a-items-next-spawn-nsec
  :create #(atom 0))
(defaola2 ^Vector2 max-item-size
  :create (fn [] (let [w (apply max (map #(.getRegionWidth ^TextureRegion %)
                                         (vals items-tr-table)))
                       h (apply max (map #(.getRegionHeight ^TextureRegion %)
                                         (vals items-tr-table)))]
                   (Vector2. w h))))
(defaola2 a-items-delete-screen-width
  :create #(atom 0))
(defaola2 ^Vector3 items-delete-screen-l-b-r
  :create #(Vector3.)
  :resize (fn [w h]
            (let [x (- (.x max-item-size))
                  y (- (.y max-item-size))
                  z (+ (.x screen-size) (.x max-item-size))]
    (.set items-delete-screen-l-b-r x y z)
    (reset! a-items-delete-screen-width (+ z x)))))

(def ^:const item-array-max 128)
(defasdefs i-idx-id i-idx-sprite i-idx-speed i-idx-cd i-idx-max)

(declare item-spawn)
(defaola2 ^"[Ljava.lang.Object;" a-items
  :create #(object-array
             (take item-array-max
                   (repeatedly
                     (fn [] (object-array
                              [nil (Sprite.) (Vector3.) (Rectangle.)])))))
  :draw-body (when @a-game-mode?
               (dotimes [i item-array-max]
                 (let [^"[Ljava.lang.Object;" item (aget a-items i)]
                   (when (aget item i-idx-id)
                     (.draw ^Sprite (aget item i-idx-sprite) the-batch)))))
  :update-body (when (and @a-dialog-nothing? @a-game-mode?)
                 (aset-int a-last-empty-idx 0 -1)
                 (let [delta (float (/ (get-nanodelta the-nc) 10000000))
                       now (get-nanotime the-nc)
                       next-timer @a-items-next-spawn-nsec
                       must-be-spawn? (< next-timer now)
                       volume-off? @a-sound-off?
                       item-delete-left (.x items-delete-screen-l-b-r)
                       item-delete-bottom (.y items-delete-screen-l-b-r)
                       item-delete-right (.z items-delete-screen-l-b-r)
                       ]
                   (dotimes [i item-array-max]
                     (let [^"[Ljava.lang.Object;" item (aget a-items i)
                           id (aget item i-idx-id)]
                       (if-not id
                         (aset-int a-last-empty-idx 0 i)
                         (let [^Sprite sprite (aget item i-idx-sprite)
                               ^Vector3 speed (aget item i-idx-speed)
                               ^Rectangle cd (aget item i-idx-cd)
                               x-locate (.getX sprite)
                               y-locate (.getY sprite)
                               x-speed (.x speed)
                               y-speed (.y speed)
                               r-speed (.z speed)
                               ]
                           (cond
                             ;; item-collision
                             (.overlaps
                               player-hit-rect
                               cd) (do
                                     ;; get-item
                                     (inc-score! id)
                                     (when-not volume-off?
                                       (.play
                                         ^Sound (id items-se-table)))
                                     (aset item i-idx-id nil))
                             ;; check item to leave
                             (< y-locate item-delete-bottom
                                ) (aset item i-idx-id nil)
                             (and
                               (< x-speed 0)
                               (< x-locate item-delete-left)
                               ) (aset item i-idx-id nil)
                             (and
                               (< 0 x-speed)
                               (< item-delete-right x-locate)
                               ) (aset item i-idx-id nil)
                             :else (let [delta-x (* delta x-speed speed-level)
                                         delta-y (* delta y-speed speed-level)
                                         delta-r (* delta r-speed speed-level)]
                                     ;; update sprite and cd
                                     (.setX sprite (+ x-locate delta-x))
                                     (.setY sprite (+ y-locate delta-y))
                                     (.rotate sprite delta-r)
                                     (.setX cd (+ (.x cd) delta-x))
                                     (.setY cd (+ (.y cd) delta-y))
                                     ))))))
                   (let [last-empty-idx (aget a-last-empty-idx 0)]
                     (when (and must-be-spawn? (not (= -1 last-empty-idx)))
                       (item-spawn (aget a-items last-empty-idx))
                       (reset! a-items-next-spawn-nsec
                               (+ (rand (/ item-spawn-interval speed-level))
                                  now))))))
  )


(defn item-spawn [^"[Ljava.lang.Object;" item]
  (let [[k desc] (rand-nth (seq item-table))
        screen-width (.x screen-size)
        screen-height (.y screen-size)
        x (- (* @a-items-delete-screen-width (rand)) (.x max-item-size))
        y screen-height
        a (rand 360)
        s-x (* 0.01 (+ 1 (rand -2)))
        s-y (* 0.01 (+ -1 (rand -3)))
        s-a (* 0.01 (+ 2 (rand -4)))
        ^TextureRegion tr (k items-tr-table)
        ^Sprite sprite (aget item i-idx-sprite)
        cd-w (float (* 0.75 (.getRegionWidth tr)))
        cd-h (float (* 0.5 (.getRegionHeight tr)))
        cd-x (float (+ x (/ (- (.getRegionWidth tr) cd-w) 2)))
        cd-y (float (+ y (/ (- (.getRegionHeight tr) cd-h) 2)))
        ]
    ;; update id
    (aset item i-idx-id k)
    ;; update sprite
    (.set sprite ^Sprite (k items-sprite-table))
    (.setPosition sprite x y)
    (.setRotation sprite a)
    ;; update speed
    (.set ^Vector3 (aget item i-idx-speed) s-x s-y s-a)
    ;; update cd
    (.set ^Rectangle (aget item i-idx-cd) cd-x cd-y cd-w cd-h)
    item))


;;; ----------------------------------------------------------------
;;; *** volume button ***

(defaola2 a-volume-button
  :create #(atom {:tr-on (.findRegion the-ta "bgm_on")
                  :tr-off (.findRegion the-ta "bgm_off")
                  :rect (Rectangle.)
                  })
  :resize (fn [w h]
            (let [button-w (.getRegionWidth
                             ^TextureRegion (:tr-on @a-volume-button))
                  button-h (.getRegionHeight
                             ^TextureRegion (:tr-on @a-volume-button))
                  x (- w button-w 2)
                  y (- h button-h 2)]
              ;; it set to corner of up-right
              (.set ^Rectangle (:rect @a-volume-button)
                    (float x) (float y) (float button-w) (float button-h))))
  :draw-body (let [^Rectangle rect (:rect @a-volume-button)]
               (.draw
                 the-batch
                 ^TextureRegion ((if @a-sound-off? :tr-off :tr-on)
                                   @a-volume-button)
                 (.x rect)
                 (.y rect)))
  :update-body (when (and
                       @a-dialog-nothing?
                       (just-released?)
                       (.contains ^Rectangle (:rect @a-volume-button)
                                  (.x touch-pos) (.y touch-pos)))
                 (change-volume! @a-sound-off?))
  )


;;; ----------------------------------------------------------------
;;; *** clan button ***
(def ^:const clan-url "https://github.com/ayamada/clan")
(defaola2 a-clan-button
  :create #(let [tr (.findRegion the-ta "clan")]
             (atom {:tr-on tr, :tr-off tr, :rect (Rectangle.) }))
  :resize (fn [w h]
            (let [button-w (.getRegionWidth
                             ^TextureRegion (:tr-on @a-clan-button))
                  button-h (.getRegionHeight
                             ^TextureRegion (:tr-on @a-clan-button))
                  x (- (.x ^Rectangle (:rect @a-volume-button)) button-w)
                  y (- h button-h 2)]
              (.set ^Rectangle (:rect @a-clan-button)
                    (float x) (float y) (float button-w) (float button-h))))
  :draw-body (let [^Rectangle rect (:rect @a-clan-button)]
               (.draw
                 the-batch
                 ^TextureRegion (:tr-on @a-clan-button)
                 (.x rect)
                 (.y rect)))
  :update-body (when (and
                       @a-dialog-nothing?
                       (just-released?)
                       (.contains ^Rectangle (:rect @a-clan-button)
                                  (.x touch-pos) (.y touch-pos)))
                 (open-dialog! "CLAN" clan-url))
  )


;;; ----------------------------------------------------------------
;;; *** license button ***
(def ^:const url-license-prefix
  "https://github.com/ayamada/clan/raw/0.1.0/doc/drop/")
(def ^:const url-license-apk (str url-license-prefix "license_apk.txt"))
(def ^:const url-license-jar (str url-license-prefix "license_jar.txt"))
(def ^:const url-license-exe (str url-license-prefix "license_exe.txt"))

(defaola2 a-license-button
  :create #(let [tr (.findRegion the-ta "license")]
             (atom {:tr-on tr, :tr-off tr, :rect (Rectangle.) }))
  :resize (fn [w h]
            (let [button-w (.getRegionWidth
                             ^TextureRegion (:tr-on @a-license-button))
                  button-h (.getRegionHeight
                             ^TextureRegion (:tr-on @a-license-button))
                  x (- (.x ^Rectangle (:rect @a-clan-button)) button-w)
                  y (- h button-h 2)]
              (.set ^Rectangle (:rect @a-license-button)
                    (float x) (float y) (float button-w) (float button-h))))
  :draw-body (let [^Rectangle rect (:rect @a-license-button)]
               (.draw
                 the-batch
                 ^TextureRegion (:tr-on @a-license-button)
                 (.x rect)
                 (.y rect)))
  :update-body (when (and
                       @a-dialog-nothing?
                       (just-released?)
                       (.contains ^Rectangle (:rect @a-license-button)
                                  (.x touch-pos) (.y touch-pos)))
                 (let [url (case (get-package-type)
                             :apk url-license-apk
                             :exe url-license-exe
                             :jar url-license-jar)]
                   (open-dialog! "LICENSE" url)))
  )


;;; ----------------------------------------------------------------
;;; *** space/drop button ***
(defaola2 a-space-button
  :create #(atom {:tr-on (.findRegion the-ta "sd_on")
                  :tr-off (.findRegion the-ta "sd_off")
                  :rect (Rectangle.)
                  })
  :resize (fn [w h]
            (let [button-w (.getRegionWidth
                             ^TextureRegion (:tr-on @a-space-button))
                  button-h (.getRegionHeight
                             ^TextureRegion (:tr-on @a-space-button))
                  x (- w button-w 2)
                  y (- (.y ^Rectangle (:rect @a-volume-button)) button-h)]
              (.set ^Rectangle (:rect @a-space-button)
                    (float x) (float y) (float button-w) (float button-h))))
  :draw-body (let [^Rectangle rect (:rect @a-space-button)]
               (.draw
                 the-batch
                 ^TextureRegion ((if @a-game-mode? :tr-off :tr-on)
                                   @a-space-button)
                 (.x rect)
                 (.y rect)))
  :update-body (when (and
                       @a-dialog-nothing?
                       (just-released?)
                       (.contains ^Rectangle (:rect @a-space-button)
                                  (.x touch-pos) (.y touch-pos)))
                 (reset! a-game-mode? (not @a-game-mode?)))
  )


;;; ----------------------------------------------------------------
;;; *** dialog (by NinePatch) ***
(def ^:const dialog-close-label "[CLOSE]")

(defaola2 a-dialog-title
  :create #(atom ""))
(defaola2 a-dialog-url
  :create #(atom ""))
(defaola2 ^Rectangle dialog-url-rect
  :create #(Rectangle.))
(defaola2 ^Rectangle dialog-close-rect
  :create #(Rectangle.))

(defn open-dialog! [title url]
  (reset! a-dialog-nothing? false)
  (reset! a-dialog-title title)
  (reset! a-dialog-url url))

(defaola2 ^NinePatch dialog-np
  :create #(NinePatch. (.findRegion the-ta "dialog") 16 16 16 16)
  :draw-body (when-not @a-dialog-nothing?
               (let [sc-w (.x screen-size)
                     sc-h (.y screen-size)
                     np-w 256
                     np-h 128
                     np-x (/ (- sc-w np-w) 2)
                     np-y (/ (- sc-h np-h) 2)
                     line-height (.getLineHeight the-font)
                     label-x (+ np-x (.getPadLeft dialog-np))
                     label-y (- (+ np-y np-h) (.getPadTop dialog-np))
                     close-w (.width (.getBounds the-font dialog-close-label))
                     close-h line-height
                     close-x (- (+ np-x np-w) (.getPadRight dialog-np) close-w)
                     close-y label-y
                     np-inner-w (- np-w
                                   (.getPadLeft dialog-np)
                                   (.getPadRight dialog-np))
                     url-b (.getWrappedBounds the-font @a-dialog-url np-inner-w)
                     url-w (.width url-b)
                     url-h (.height url-b)
                     url-x (/ (- sc-w url-w) 2)
                     url-y (+ np-y (.getPadBottom dialog-np) url-h)
                     ]
                 (.set dialog-close-rect
                       close-x (- close-y close-h 8) close-w (+ close-h 16))
                 (.set dialog-url-rect
                       url-x (- url-y url-h 8) url-w (+ url-h 16))
                 (.draw dialog-np the-batch np-x np-y np-w np-h)
                 (.setColor the-font Color/BLACK)
                 (.draw the-font the-batch @a-dialog-title label-x label-y)
                 (.setColor the-font Color/BLUE)
                 (.draw the-font the-batch dialog-close-label close-x close-y)
                 (.drawWrapped
                   the-font the-batch @a-dialog-url url-x url-y np-inner-w)
                 ))
  :update-body (when (and (not @a-dialog-nothing?) (just-released?))
                 (let [x (.x touch-pos)
                       y (.y touch-pos)]
                   (cond
                     (.contains dialog-url-rect x y) (open-url @a-dialog-url)
                     (.contains
                       dialog-close-rect x y) (reset! a-dialog-nothing? true)
                     :else nil)))
  )


;;; ----------------------------------------------------------------
;;; *** score ***
(defaola2 score-a-color :create #(Color. 0.8 0.8 1.0 0.8))
(defaola2 score-b-color :create #(Color. 1.0 0.8 0.8 0.8))
(defaola2 score-c-color :create #(Color. 0.8 1.0 0.8 0.8))
(declare update-score!)
(defaola2 ^BitmapFontCache score-a-cache
  :create #(BitmapFontCache. the-font)
  :resize (fn [w h] (update-score! :score-a))
  :draw-body (when @a-game-mode? (.draw score-a-cache the-batch))
  )
(defaola2 ^BitmapFontCache score-b-cache
  :create #(BitmapFontCache. the-font)
  :resize (fn [w h] (update-score! :score-b))
  :draw-body (when @a-game-mode? (.draw score-b-cache the-batch))
  )
(defaola2 ^BitmapFontCache score-c-cache
  :create #(BitmapFontCache. the-font)
  :resize (fn [w h] (update-score! :score-c))
  :draw-body (when @a-game-mode? (.draw score-c-cache the-batch))
  )

(definline solve-score-info [k]
  `(case ~k
     :score-a [2 score-a-cache score-a-color]
     :score-b [1 score-b-cache score-b-color]
     :score-c [0 score-c-cache score-c-color]))

(def score-key->idx {:score-a 0, :score-b 1, :score-c 2})

(defn update-score! [score-key]
  (let [score (nth @a-scores (score-key->idx score-key))
        score-str (str score)
        [lv ^BitmapFontCache cache ^Color color] (solve-score-info score-key)
        line-height (.getLineHeight the-font)
        score-width (.width (.getBounds the-font score-str))
        x (- (.x screen-size) 8 score-width (* 48 lv))
        y (+ 2 line-height)]
    (doto cache
      (.setText score-str x y)
      (.setColor color))))

(defn inc-score! [item-key]
  (let [item-desc (item-key item-table)
        score-key (:type item-desc)
        old-scores @a-scores
        score-a (nth old-scores 0)
        score-b (nth old-scores 1)
        score-c (nth old-scores 2)
        new-scores (case score-key
                     :score-a [(inc score-a) score-b score-c]
                     :score-b [score-a (inc score-b) score-c]
                     :score-c [score-a score-b (inc score-c)])
        ]
    (reset! a-scores new-scores)
    (update-score! score-key)
    ))


;;; ----------------------------------------------------------------
;;; *** simple-console ***
(defaola2 ^Color color-console :create #(Color. 1 1 1 0.2))
(def ^:const margin-x 2)
(def ^:const margin-y 2)

(defaola2 a-simple-console-timer :create #(atom 0))

(declare update-cache!)

(defaola2 ^BitmapFontCache simple-console-bfc
  :create #(BitmapFontCache. the-font)
  :draw-body (when @a-game-mode? (.draw simple-console-bfc the-batch))
  :update-body (let [now (get-nanotime the-nc)]
                 (when (< @a-simple-console-timer now)
                   (reset! a-simple-console-timer
                           (+ now console-update-interval-nsec))
                   (update-cache!)))
  )

;;; TODO: reduce GC
(defn construct-simple-console-str []
  (str
    (if-release "RELEASE: " "DEBUG: ") claninfo/project-version "\n"
    (purge-code-when-release
      (str "BDT: " claninfo/build-date "\n"))
    (str "BNM: " claninfo/build-number "\n")
    (purge-code-when-release
      (str "SYS: " (.. Gdx app (getType)) "\n"))
    (purge-code-when-release
      (str "SDK: " (.. Gdx app (getVersion)) "\n"))
    "FPS: " (.. Gdx graphics (getFramesPerSecond)) "\n"
    "MEM: " (format "%09d" (.. Gdx app (getNativeHeap))) "\n"
    ;(purge-code-when-release
    ;  (str "ITEMS: " (count @a-items) "\n"))
    ;(purge-code-when-release
    ;  (str "BG_STARS: " (count @a-background-stars) "\n"))
    ))

(defn update-cache! []
  (let [all-text (construct-simple-console-str)
        x margin-x
        y (- (.y screen-size) margin-y)
        ]
    (.setColor simple-console-bfc color-console)
    (.setMultiLineText simple-console-bfc all-text x y)))





;;; ----------------------------------------------------------------

(declare main-resume)

(defn main-create []
  ;(prn 'is-release? claninfo/is-release?)
  ;(prn 'build-target claninfo/build-target)
  ;(prn 'java.class.path (System/getProperty "java.class.path"))
  ;(prn 'sun.java.command (System/getProperty "sun.java.command"))
  ;(prn "./pack.atlas exists?"
  ;     (.. Gdx files (internal "pack.atlas") (exists)))
  ;(prn "assets/pack.atlas exists?"
  ;     (.. Gdx files (internal "assets/pack.atlas") (exists)))
  ;(prn "assets/assets/pack.atlas exists?"
  ;     (.. Gdx files (internal "assets/assets/pack.atlas") (exists)))
  (set-display-bootlogo-in-android!)
  (aola2-handler-create!)
  (update-music!)
  (main-resume))


(defn main-resize [real-w real-h]
  (let [w (max min-width real-w)
        h (max min-height real-h)]
    (aola2-handler-resize! w h the-batch the-camera)))



(defn main-pause []
  ;(purge-code-when-release
  ;  (prn (str "FPS: " (.. Gdx graphics (getFramesPerSecond)))))
  (save-data!)
  (aola2-handler-pause!))


(defn main-resume []
  (aola2-handler-resume!))


(defn main-render []
  (try
    (.. Gdx gl (glClearColor 0.0 0.0 0.1 1.0)) ; R G B A
    (.. Gdx gl (glClear (. GL10 GL_COLOR_BUFFER_BIT)))
    (aola2-handler-render! the-batch)
    (catch Exception e
      (main-pause)
      (throw e))))


(defn main-dispose []
  (aola2-handler-dispose!))

(aola2-entries-term!)

;;; ----------------------------------------------------------------

(defn generate-al []
  (proxy [ApplicationListener] []
    (create [] (main-create))
    (resume [] (main-resume))
    (resize [w h] (main-resize w h))
    (render [] (main-render))
    (pause [] (main-pause))
    (dispose [] (main-dispose))
    ))

;(defn generate-al []
;  (proxy [ApplicationListener] []
;    (create [] nil)
;    (resume [] nil)
;    (resize [w h] nil)
;    (render []
;      (.. Gdx gl (glClearColor (rand) (rand) (rand) 1.0))
;      (.. Gdx gl (glClear GL10/GL_COLOR_BUFFER_BIT)))
;    (pause [] nil)
;    (dispose [] nil)
;    ))

;;; ----------------------------------------------------------------


