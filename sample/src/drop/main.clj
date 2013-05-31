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
    (com.badlogic.gdx.math Vector2 Vector3 Rectangle Matrix4)
    (com.badlogic.gdx.utils TimeUtils Disposable)
    (java.lang.reflect Method)
    (java.util.concurrent RejectedExecutionException)
    (java.util.zip Deflater DeflaterOutputStream InflaterInputStream)
    (java.io File OutputStream ByteArrayOutputStream)
    )
  (:use
    [jp.ne.tir.clan.clanutil]
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
(def console-update-interval-nsec 500000000)


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
           ;; TODO: ↑のjava.class.pathからの取得は問題がある、要他手段
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

;;; ----------------------------------------------------------------
;;; *** AOLA2: Auto Object in LibGDX's Application #2 ***
(def ae-symbol '_aola2-entries)

(defmacro aola2-entries-init! []
  ;; NB: _aola2-entriesはutil02の外に作られる
  (list 'def ae-symbol '(atom nil)))
(defmacro aola2-entries-term! []
  ;; 以下の基準でコンパクト化を行う。
  ;; - map内の、keyが:***-bodyのエントリを消去
  ;; - map内の、valがnilのエントリを消去
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
    ;; そして最後に、ae-symbolの再定義を行うコードを出力
    (list 'def ae-symbol (list 'atom (list 'quote new-entries)))
    ))

(defn order-aola2-entries [entries]
  ;; TODO: 並び換え順をもう少し熟考する必要があるかも
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
                         :ns *ns* ; 呼出元の名前空間を保存する
                         :order (:order param) ; 順序情報
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
           ;(when-debug (prn handle#))
           (handle# w# h#))))))

(defmacro aola2-handler-render! [batch]
  ;; NB: これのみ、インライン展開を行う(速度稼ぎの為)
  (let [entries @(eval ae-symbol)
        draw-bodies (map #(:draw-body %) entries)
        sense-bodies (map #(:sense-body %) entries)
        update-bodies (map #(:update-body %) entries)
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
       :sense-body (let [now# (TimeUtils/nanoTime)
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

(defn load-data! []
  (let [edn-str (or
                  (keep-code-when-desktop
                    (let [^String f (get-local-path save-file)]
                      (when (.exists (FileHandle. f))
                        (inflate-read-from-file f))))
                  (keep-code-when-android
                    (doto (.. Gdx app (getPreferences "drop"))
                      (.getString pref "edn" "")))
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
        (doto (.. Gdx app (getPreferences "drop"))
          (.putString "edn" edn-str)
          (.flush)))
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

(definline distance->transparency [dist] `(Math/atan (/ ~dist 100)))
(definline star-spawn []
  `(let [tp# (+ 0.3 (rand))]
     {:angle (rand 360)
      :distance @a-star-initial-dist
      :tp-orig tp#
      :tp-cur (if (< 1 tp#) 1 tp#)
      :speed-level (+ 0.98 (rand 0.01))}))

(defaola2 a-background-star-next-spawn-nsec
  :create #(atom 0))

(defaola2 a-background-stars
  :create #(atom nil)
  :draw-body (do
               (do-each
                 @a-background-stars
                 [{angle :angle, dist :distance, tp :tp-cur}]
                 (let [v (doto (Vector2. 1 0)
                           (.setAngle angle)
                           (.mul (float dist)))
                       x (+ (.x screen-center) (.x v))
                       y (+ (.y screen-center) (.y v))
                       ]
                   (.setColor the-batch 1 1 1 tp)
                   (.draw the-batch star-tr x y)))
               (.setColor the-batch 1 1 1 1))
  :update-body (let [interval (/ (get-nanodelta the-nc) 10000000)
                     now (get-nanotime the-nc)
                     updated-stars (reduce
                                     (fn [coll star]
                                       (let [{angle :angle
                                              dist :distance
                                              tp :tp-orig
                                              s-l :speed-level} star
                                             new-dist (* dist (Math/pow s-l interval))
                                             d-tp (distance->transparency dist)
                                             tp-merge (* tp d-tp)
                                             tp-lpf (min 1 tp-merge)
                                             ]
                                         (if (< tp-lpf 0.1)
                                           coll
                                           (conj coll
                                                 (merge star
                                                        {:distance new-dist
                                                         :tp-cur tp-lpf})))))
                                     nil
                                     @a-background-stars)
                     ]
                 (reset! a-background-stars updated-stars)
                 (when (< @a-background-star-next-spawn-nsec now)
                   (reset! a-background-star-next-spawn-nsec
                           (+ now (rand (/ star-spawn-interval speed-level))))
                   (swap! a-background-stars conj (star-spawn))))
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
             ;(.setOrigin sprite (/ (.getWidth sprite) 2) 0) ; なんか動かない
             (.setPosition sprite x y)
             (.set player-hit-rect
                   (float (+ x (/ (.getWidth sprite) 2))) ; x
                   (float (+ y (.getHeight sprite))) ; y
                   (float 1) ; width
                   (float 1)) ; height
             sprite)
  :draw-body (when @a-game-mode? (.draw player-sprite the-batch))
  ;:sense-body (when @a-game-mode? ...) ; 今回はupdateと一緒にしてしまう
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


(declare item-spawn)
(defaola2 a-items
  :create #(atom nil)
  :draw-body (when @a-game-mode?
               (do-each
                 @a-items
                 [{^Sprite sprite :sprite}]
                 (.draw sprite the-batch)))
  :update-body (when (and @a-dialog-nothing? @a-game-mode?)
                 (let [delta (/ (get-nanodelta the-nc) 10000000)
                       now (get-nanotime the-nc)
                       next-timer @a-items-next-spawn-nsec
                       must-be-spawn? (< next-timer now)
                       volume-off? @a-sound-off?
                       item-delete-left (.x items-delete-screen-l-b-r)
                       item-delete-bottom (.y items-delete-screen-l-b-r)
                       item-delete-right (.z items-delete-screen-l-b-r)
                       r1 (filter
                            identity
                            (map
                              (fn [item]
                                (let [{id :id
                                       ^Sprite sprite :sprite
                                       ^Vector3 speed :speed
                                       ^Rectangle cd :cd} item
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
                                            false)
                                    ;; check item erase
                                    (< y-locate item-delete-bottom) false
                                    (and
                                      (< x-speed 0)
                                      (< x-locate item-delete-left)) false
                                    (and
                                      (< 0 x-speed)
                                      (< item-delete-right x-locate)) false
                                    :else (let [delta-x (* delta x-speed)
                                                delta-y (* delta y-speed)
                                                delta-r (* delta r-speed)]
                                            ;; update sprite and cd
                                            (.setX sprite (+ x-locate delta-x))
                                            (.setY sprite (+ y-locate delta-y))
                                            (.rotate sprite delta-r)
                                            (.setX cd (+ (.x cd) delta-x))
                                            (.setY cd (+ (.y cd) delta-y))
                                            item))))
                              @a-items))
                       r2 (if must-be-spawn? (cons (item-spawn) r1) r1)
                       ]
                   (when must-be-spawn?
                     (reset! a-items-next-spawn-nsec
                             (+ now
                                (rand (/ item-spawn-interval speed-level)))))
                   (reset! a-items r2))))


(defn item-spawn []
  (let [[k desc] (rand-nth (seq item-table))
        screen-width (.x screen-size)
        screen-height (.y screen-size)
        x (- (* @a-items-delete-screen-width (rand)) (.x max-item-size))
        y screen-height
        a (rand 360)
        s-x (+ 1 (rand -2))
        s-y (+ -1 (rand -3))
        s-a (+ 2 (rand -4))
        speed (Vector3. s-x s-y s-a)
        ^TextureRegion tr (k items-tr-table)
        ^Sprite sprite (Sprite. tr)
        cd-w (float (* 0.75 (.getRegionWidth tr)))
        cd-h (float (* 0.5 (.getRegionHeight tr)))
        cd-x (float (+ x (/ (- (.getRegionWidth tr) cd-w) 2)))
        cd-y (float (+ y (/ (- (.getRegionHeight tr) cd-h) 2)))
        cd (Rectangle. cd-x cd-y cd-w cd-h)]
    ;; update sprite
    (.setPosition sprite x y)
    (.setRotation sprite a)
    {:id k
     :sprite sprite
     :speed speed
     :cd cd
     }))


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
  "https://github.com/ayamada/clan/raw/0.0.3-EXPERIMENTAL/doc/drop/")
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

(defn construct-simple-console-str []
  ;; TODO: 一部の変化しない部分はキャッシュする事
  (str
    (if-release "RELEASE: " "DEBUG: ") (comment "TODO: バージョン表示") "\n"
    (purge-code-when-release
      (str "BDT: " clan-info-build-date "\n"))
    (str "BNM: " clan-info-build-number "\n")
    (purge-code-when-release
      (str "SYS: " (.. Gdx app (getType)) "\n"))
    (purge-code-when-release
      (str "SDK: " (.. Gdx app (getVersion)) "\n"))
    "FPS: " (.. Gdx graphics (getFramesPerSecond)) "\n"
    "MEM: " (format "%09d" (.. Gdx app (getNativeHeap))) "\n"
    (purge-code-when-release
      (str "ITEMS: " (count @a-items) "\n"))
    (purge-code-when-release
      (str "BG_STARS: " (count @a-background-stars) "\n"))
    ))

(defn update-cache! []
  (let [all-text (construct-simple-console-str)
        x margin-x
        y (- (.y screen-size) margin-y)
        ]
    (.setColor simple-console-bfc color-console)
    (.setMultiLineText simple-console-bfc all-text x y)))





;;; ----------------------------------------------------------------
;;; *** process dispatch ***
;(def a-touched? (atom false))
;
;(defn process-draw! []
;  (with-batch
;    (draw-background!)
;    (when @a-game-mode?
;      (draw-player!)
;      (draw-items!))
;    (draw-buttons!)
;    (when @a-game-mode?
;      (draw-score!)
;      (draw-simple-console!))
;    (draw-dialog!)
;    (draw-eval-console!)
;    ))
;
;
;;; ----------------------------------------------------------------
;;; *** main ***
;
;
;(defn drop-create []
;  (when do-prof? (println "!!! do-prof? is true (slow) !!!"))
;  (when definline-is-fn? (println "!!! definline-is-fn? is true (slow) !!!"))
;  (init-batch!)
;  (init-camera!)
;  (init-font!)
;  (init-score!)
;  (init-dialog!)
;  (unregister-all-button!)
;  (register-volume-button!)
;  (register-clan-button!)
;  (register-license-button!)
;  (register-space-button!)
;  (init-buttons!)
;  (init-player!)
;  (init-items!)
;  (init-background!)
;  (init-eval-console!)
;  (when do-prof? (eval '(android.os.Debug/startMethodTracing "drop"))))
;
;
;(defn drop-resize [w-orig h-orig]
;  (let [w (clamp-num (first min-screen-size) w-orig (first max-screen-size))
;        h (clamp-num (second min-screen-size) h-orig (second max-screen-size))]
;    (update-screen-rect! w h)
;    (.setToOrtho ^OrthographicCamera (camera) false w h)
;    (.update ^Camera (camera))
;    (.setProjectionMatrix ^SpriteBatch (batch)
;                          (.combined ^OrthographicCamera (camera)))
;    (update-buttons! w h)
;    (update-player-max-x!)
;    (clamp-player-locate-x!)
;    (update-all-score!)
;    (update-items-delete-rect!)
;    (update-background-info!)
;    (update-simple-console!)
;    (update-eval-console-text!)
;    ))
;
;
;
;
;(defn drop-render []
;  (try
;    (process-draw!)
;    (update-touch-pos!)
;    (let [delta (get-delta)
;          prev-touched? @a-touched?
;          is-touched? (.. Gdx input (isTouched))
;          just-touched? (.. Gdx input (justTouched))
;          _ (and (or is-touched? just-touched?) (update-touch-pos!))
;          touch-x (.x touch-pos)
;          touch-y (.y touch-pos)
;          game-mode? @a-game-mode?
;          dialog-nothing? @a-dialog-nothing?
;          ]
;      (when dialog-nothing?
;        (process-buttons! just-touched? touch-x touch-y))
;      (when (and game-mode? dialog-nothing?)
;        (process-player! delta prev-touched? is-touched? touch-x touch-y)
;        (process-item! delta))
;      (process-background! delta)
;      (when game-mode?
;        (process-simple-console!))
;      (process-eval-console!)
;      (when (not= prev-touched? is-touched?)
;        (reset! a-touched? is-touched?))
;      (when (and just-touched? (not dialog-nothing?))
;        (process-dialog! touch-x touch-y)))
;    (catch Exception e (drop-pause) (throw e))))


(declare main-resume)

(defn main-create []
  (aola2-handler-create!)
  (update-music!)
  (main-resume))


(defn main-resize [real-w real-h]
  (let [w (max min-width real-w)
        h (max min-height real-h)]
    (aola2-handler-resize! w h the-batch the-camera)))



(defn main-pause []
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
;      (.. Gdx gl (glClear (. GL10 GL_COLOR_BUFFER_BIT))))
;    (pause [] nil)
;    (dispose [] nil)
;    ))

;;; ----------------------------------------------------------------


;; 日本語コードはutf-8
