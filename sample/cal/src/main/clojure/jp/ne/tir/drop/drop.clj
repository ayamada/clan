(ns jp.ne.tir.drop.drop
  (:import
    (jp.ne.tir.clan Info)
    (com.badlogic.gdx Gdx Preferences Input$Keys)
    (com.badlogic.gdx.audio Music Sound)
    (com.badlogic.gdx.files FileHandle)
    (com.badlogic.gdx.graphics GL10 Camera OrthographicCamera Texture Color
                               Pixmap Pixmap$Format)
    (com.badlogic.gdx.graphics.g2d SpriteBatch BitmapFont BitmapFontCache)
    (com.badlogic.gdx.math Vector2 Vector3 Rectangle Matrix4)
    (com.badlogic.gdx.utils TimeUtils Disposable)
    (java.lang.reflect Method)
    (java.util.concurrent RejectedExecutionException)
    ))
;; this game is based on http://code.google.com/p/libgdx/wiki/SimpleApp .

(set! *warn-on-reflection* true) ; for performance tuning


;; ----------------------------------------------------------------
;; *** consts ***
(def ^:const do-prof? false) ; see http://doc.intra.tir.ne.jp/devel/clan/memo
(def ^:const macron-is-fn? false) ; for stacktrace

(def ^:const pref-name (str "PREF-" Info/projectGroupId
                            "-" Info/projectArtifactId
                            "-" Info/projectClassifier)) ; must be unique

(def ^:const assets-dir "drop")

(def min-screen-size [256 256])
(def max-screen-size [nil nil])

(def speed-level 100)
(def player-locate-y 32)

(def ^:const bgm-file "bgm.ogg")
(def ^:const player-img-file "tsubo.png")

(def ^:const item-img-suffix ".png")
(def ^:const item-se-suffix ".wav")
(def ^:const item-table
  {:star {:type :score-a}
   :moon {:type :score-a}
   :fish {:type :score-b}
   :fruit {:type :score-b}
   :flower {:type :score-c}
   :rondel {:type :score-c}
   :hdd {:type :score-c}
   }) ; :star has assets/drop/star.png, assets/drop/star.wav

(def item-spawn-interval 100000000000)
(def star-spawn-interval 10000000000)
(def console-update-interval-nsec 500000000)


;; ----------------------------------------------------------------
;; *** misc utilities ***
;; macro as fn for profiler, cannot use & in args
(defmacro defmacron [mname margs mbody]
  (if macron-is-fn?
    (let [tmp-mname (gensym mname)
          fn-body `(~tmp-mname ~@margs)]
      `(do
         (defmacro ~tmp-mname ~margs ~mbody)
         (defn ~mname ~margs ~fn-body)))
    (list 'defmacro mname margs mbody)))

;; all asset files are in "assets/ASSETS-DIR/" for safety.
(defn assets-file ^FileHandle [path]
  (.. Gdx files (internal (str assets-dir "/" path))))

(defmacron get-delta [] `(* (.. Gdx graphics (getDeltaTime)) speed-level))

(defmacron clamp-num [min-n n max-n]
  `(let [min-n# ~min-n n# ~n max-n# ~max-n
         n2# (if min-n# (max min-n# n#) n#)]
     (if max-n# (min n2# max-n#) n2#)))


;; ----------------------------------------------------------------
;; *** disposables ***
;; all objs with need .dispose, those must registered
(def a-disposables (atom nil))

(defn register-disposer! [obj]
  (let [can-dispose? (some #(= "dispose" (.getName ^Method %))
                           (.getMethods (class obj)))]
    (when-not can-dispose?
      (throw (Exception. (str obj " cannot dispose"))))
    (swap! a-disposables conj obj)))

(defn dispose-all! []
  (dorun (map #(.dispose ^Disposable %) @a-disposables))
  (reset! a-disposables nil))


;; ----------------------------------------------------------------
;; *** gdx's preferences ***
;; a capacity of pref is very small.
;; http://developer.android.com/reference/java/util/prefs/Preferences.html#MAX_VALUE_LENGTH
;; but it may limited to use only about 1-2k for safety.
(def a-prefs (atom {}))

(defmacron pref [kwd] `^Preferences(~kwd @a-prefs))

(defmacron pref! [kwd new-val] `(swap! a-prefs merge {~kwd ~new-val}))

(defmacron get-gdx-pref [] `(.. Gdx app (getPreferences pref-name)))

(defn load-pref-from-storage! []
  (let [^Preferences gdx-pref (get-gdx-pref)
        loaded {:volume-off? (.getBoolean gdx-pref "volume-off?" false)
                :score-a (long (.getInteger gdx-pref "score-a" 0))
                :score-b (long (.getInteger gdx-pref "score-b" 0))
                :score-c (long (.getInteger gdx-pref "score-c" 0))
                }]
    (swap! a-prefs merge loaded)))

(defn save-pref-to-storage! []
  (let [^Preferences gdx-pref (get-gdx-pref)]
    (.putBoolean gdx-pref "volume-off?" (pref :volume-off?))
    (.putInteger gdx-pref "score-a" (int (pref :score-a)))
    (.putInteger gdx-pref "score-b" (int (pref :score-b)))
    (.putInteger gdx-pref "score-c" (int (pref :score-c)))
    (.flush gdx-pref)))


;; ----------------------------------------------------------------
;; *** music ***
(def a-music (atom nil))

(defn init-music! []
  (let [bgm (.. Gdx audio (newMusic (assets-file bgm-file)))]
    (.setLooping bgm true)
    (if (pref :volume-off?) (.stop bgm) (.play bgm))
    (register-disposer! bgm)
    (reset! a-music bgm)))

(defn change-volume! [on-off]
  (let [pref-name (str "CBL-" Info/projectGroupId "-" Info/projectArtifactId
                       "-" Info/projectClassifier) ; see BootLoader.java
        cbl-pref (.. Gdx app (getPreferences pref-name))]

    (.putBoolean cbl-pref "PLAY_JINGLE" on-off)
    (.flush cbl-pref))
  (pref! :volume-off? (not on-off))
  (if (pref :volume-off?) (.stop ^Music @a-music) (.play ^Music @a-music)))


;; ----------------------------------------------------------------
;; *** gdx's batch ***
(def a-batch (atom nil))

(defmacron batch [] `^SpriteBatch@a-batch)

(defn init-batch! []
  (let [b (SpriteBatch.)] (register-disposer! b) (reset! a-batch b)))

(defmacro with-batch [& bodies]
  `(do
     (.begin ^SpriteBatch (batch))
     (try
       ~@bodies
       (finally (.end ^SpriteBatch (batch))))))


;; ----------------------------------------------------------------
;; *** gdx's camera ***
(def a-camera (atom nil))
(def ^Vector2 screen-rect (Vector2.))

(defmacron camera [] `^OrthographicCamera@a-camera)

(defn init-camera! [] (reset! a-camera (OrthographicCamera.)))

(defmacron get-screen-width [] `(.x screen-rect))
(defmacron get-screen-height [] `(.y screen-rect))
(defmacron update-screen-rect! [w h]
  `(do (set! (.x screen-rect) ~w) (set! (.y screen-rect) ~h)))

(defmacron get-touch-pos []
  `(let [^Vector3 pos# (Vector3.)]
     (.. pos# (set (.. Gdx input (getX)) (.. Gdx input (getY)) 0))
     (.. ^OrthographicCamera (camera) (unproject pos#))
     pos#))


;; ----------------------------------------------------------------
;; *** gdx's font ***
(def a-font (atom nil))
(def a-font-line-height (atom nil))

(defmacron font [] `^BitmapFont@a-font)

(defn init-font! []
  (let [f (BitmapFont.)]
    ;(.setFixedWidthGlyphs f (apply str (map char (range 32 127))))
    (.setFixedWidthGlyphs f "0123456789")
    (reset! a-font-line-height (.getLineHeight f))
    (register-disposer! f)
    (reset! a-font f)))


; ----------------------------------------------------------------
;; *** volume button ***
(def ^:const VOLUME-BUTTON-WIDTH 64)
(def ^:const VOLUME-BUTTON-HEIGHT 64)
(def a-volume-button-on-tex (atom nil))
(def a-volume-button-off-tex (atom nil))
(def ^Rectangle volume-button-rect (Rectangle.))

(defn init-volume-button! []
  (let [button-frame-color (Color. 0.5 0.5 0.5 0.5)
        button-color (Color. 0 0.2 0.4 1)
        on-pm (doto (Pixmap. VOLUME-BUTTON-WIDTH VOLUME-BUTTON-HEIGHT
                             Pixmap$Format/RGBA8888)
                (.setColor button-frame-color)
                (.fillCircle 32 32 24)
                (.setColor button-color)
                (.fillCircle 32 32 20)
                (.setColor Color/LIGHT_GRAY)
                (.drawCircle 32 32 8)
                )
        off-pm (doto (Pixmap. VOLUME-BUTTON-WIDTH VOLUME-BUTTON-HEIGHT
                              Pixmap$Format/RGBA8888)
                (.setColor button-frame-color)
                (.fillCircle 32 32 24)
                (.setColor button-color)
                (.fillCircle 32 32 20)
                (.setColor Color/GRAY)
                (.drawLine 28 28 36 36)
                (.drawLine 28 36 36 28)
                 )
        on-tex (Texture. on-pm)
        off-tex (Texture. off-pm)
        ]
    (.dispose on-pm)
    (.dispose off-pm)
    (register-disposer! on-tex)
    (register-disposer! off-tex)
    (reset! a-volume-button-on-tex on-tex)
    (reset! a-volume-button-off-tex off-tex)))

(defmacron get-volume-button-tex []
  `^Texture@(if (pref :volume-off?)
              a-volume-button-off-tex
              a-volume-button-on-tex))

(defn update-volume-button-rect! []
  ;; it set to corner of up-right
  (let [^Texture tex (get-volume-button-tex)
        w (.getWidth tex)
        h (.getHeight tex)
        x (- (get-screen-width) w)
        y (- (get-screen-height) h 1)]
    (.set volume-button-rect x y (float w) (float h))))

(defmacron draw-volume-button! []
  `(.draw ^SpriteBatch (batch)
          ^Texture (get-volume-button-tex)
          (.x volume-button-rect)
          (.y volume-button-rect)))

(defmacron process-volume-button! [just-touched touch-x touch-y]
  `(when (and
           ~just-touched
           (.contains volume-button-rect ~touch-x ~touch-y))
     (change-volume! (pref :volume-off?))))


;; ----------------------------------------------------------------
;; *** player ***
(def a-player-tex (atom nil))
(def a-player-tex-width-half (atom 0))
(def a-player-tex-height (atom 0))
(def a-player-max-x (atom 0))
(def ^Rectangle player-hit-rect (Rectangle.))

(defn init-player! []
  (let [tex (Texture. (assets-file player-img-file))
        tex-width-half (/ (.getWidth tex) 2)
        tex-height (.getHeight tex)
        ]
    (reset! a-player-tex tex)
    (register-disposer! tex)
    (reset! a-player-tex-width-half tex-width-half)
    (reset! a-player-tex-height tex-height)
    ;; hit-rect (NOT texture-display-position-rect)
    (.set player-hit-rect
          (float (/ (.getWidth tex) 2)) ; x
          (float (+ player-locate-y (.getHeight tex))) ; y
          (float 1) ; width
          (float 1)) ; height
    ))

(defmacron update-player-max-x! []
  `(reset! a-player-max-x (- (get-screen-width) @a-player-tex-width-half)))
(defmacron update-player-locate-x! [x] `(set! (. player-hit-rect x) (float ~x)))
(defmacron get-player-locate-x [] `(.x player-hit-rect))
(defmacron clamp-player-locate-x [x]
  `(let [x# ~x
         min-x# @a-player-tex-width-half
         max-x# @a-player-max-x]
     (cond
       (< x# min-x#) min-x#
       (< max-x# x#) max-x#
       :else x#)))
(defmacron clamp-player-locate-x! []
  `(set! (. player-hit-rect x) (clamp-player-locate-x (get-player-locate-x))))

(defmacron draw-player! []
  `(.draw ^SpriteBatch (batch)
          ^Texture @a-player-tex
          (float (- (.x player-hit-rect) @a-player-tex-width-half))
          (float (- (.y player-hit-rect) @a-player-tex-height))))

(defmacron process-player! [is-touched touch-x touch-y]
  `(do
     ;; move by touch
     (when ~is-touched
       (let [x# ~touch-x y# ~touch-y]
         ;; exclude to touch volume button
         (when-not (.contains volume-button-rect x# y#)
           (update-player-locate-x! (clamp-player-locate-x x#)))))
     ;; move by keyboard
     (let [pressed-l# (.. Gdx input (isKeyPressed Input$Keys/LEFT))
           pressed-r# (.. Gdx input (isKeyPressed Input$Keys/RIGHT))]
       (when (or
               (and pressed-l# (not pressed-r#))
               (and (not pressed-l#) pressed-r#))
         (let [player-x# (get-player-locate-x)
               delta# (get-delta)
               new-x# (clamp-player-locate-x (if pressed-l#
                                               (- player-x# delta#)
                                               (+ player-x# delta#)))]
           (update-player-locate-x! new-x#))))))


;; ----------------------------------------------------------------
;; *** score ***
(def a-score-a-cache (atom nil))
(def a-score-b-cache (atom nil))
(def a-score-c-cache (atom nil))
(def score-a-color (Color. 0.8 0.8 1.0 0.8))
(def score-b-color (Color. 1.0 0.8 0.8 0.8))
(def score-c-color (Color. 0.8 1.0 0.8 0.8))

(defn init-score! []
  (reset! a-score-a-cache (BitmapFontCache. (font)))
  (reset! a-score-b-cache (BitmapFontCache. (font)))
  (reset! a-score-c-cache (BitmapFontCache. (font))))

(defmacron solve-score-info [k]
  `(case ~k
     :score-a [0 a-score-a-cache score-a-color]
     :score-b [1 a-score-b-cache score-b-color]
     :score-c [2 a-score-c-cache score-c-color]))

(defmacron update-score! [score-key ^Long score]
  `(let [score-key# ~score-key
         score-str# (.toString ~score)
         [lv# a-cache# color#] (solve-score-info score-key#)
         line-height# @a-font-line-height
         x# (- (get-screen-width)
               1
               (.width (.getBounds ^BitmapFont (font) score-str#)))
         y# (- (.y volume-button-rect) 1 (* line-height# lv#))]
     (doto ^BitmapFontCache @a-cache#
       (.setText score-str# x# y#)
       (.setColor ^Color color#))))

(defn update-all-score! []
  (update-score! :score-a (pref :score-a))
  (update-score! :score-b (pref :score-b))
  (update-score! :score-c (pref :score-c)))

(defmacron inc-score! [item-key]
  `(let [item-desc# (~item-key item-table)
         score-key# (:type item-desc#)
         new-score# (+ 1 (pref score-key#))]
     (pref! score-key# new-score#)
     (update-score! score-key# new-score#)))

(defmacron draw-score! []
  `(let [b# (batch)]
     (.draw ^BitmapFontCache @a-score-a-cache b#)
     (.draw ^BitmapFontCache @a-score-b-cache b#)
     (.draw ^BitmapFontCache @a-score-c-cache b#)))


;; ----------------------------------------------------------------
;; *** items ***
(def a-items (atom nil))
(def a-items-tex (atom {}))
(def a-items-se (atom {}))
(def a-items-next-spawn-nsec (atom 0))
(def ^Vector3 items-delete-screen-l-b-r (Vector3.))
(def ^Vector2 max-item-size (Vector2.))
(def a-items-delete-screen-width (atom 0))

(defn init-items! []
  (dorun
    (map
      (fn [[k desc]]
        (let [tex (Texture. (assets-file (str (name k)
                                              item-img-suffix)))
              tex-w (.getWidth tex)
              tex-h (.getHeight tex)
              se (.. Gdx audio (newSound (assets-file (str (name k)
                                                           item-se-suffix))))]
          (when (< (.x max-item-size) tex-w) (set! (.x max-item-size) tex-w))
          (when (< (.y max-item-size) tex-h) (set! (.y max-item-size) tex-h))
          (register-disposer! tex)
          (register-disposer! se)
          (swap! a-items-tex merge {k tex})
          (swap! a-items-se merge {k se})))
      item-table)))

(defn update-items-delete-rect! []
  (let [x (- (.x max-item-size))
        y (- (.y max-item-size))
        z (+ (get-screen-width)(.x max-item-size))]
    (.set items-delete-screen-l-b-r x y z)
    (reset! a-items-delete-screen-width (+ z x))))

(defmacron item-spawn []
  `(let [[k# desc#] (rand-nth (seq item-table))
         screen-width# (get-screen-width)
         screen-height# (get-screen-height)
         x# (- (* @a-items-delete-screen-width (rand)) (.x max-item-size))
         y# screen-height#
         a# (rand 360)
         locate# (Vector3. x# y# a#)
         s-x# (+ 1 (rand -2))
         s-y# (+ -1 (rand -3))
         s-a# (+ 2 (rand -4))
         speed# (Vector3. s-x# s-y# s-a#)
         ^Texture tex# (k# @a-items-tex)
         cd-w# (float (* 0.75 (.getWidth tex#)))
         cd-h# (float (* 0.5 (.getHeight tex#)))
         cd-x# (float (+ x# (/ (- (.getWidth tex#) cd-w#) 2)))
         cd-y# (float (+ y# (/ (- (.getHeight tex#) cd-h#) 2)))
         cd# (Rectangle. cd-x# cd-y# cd-w# cd-h#)]
     {:id k# :locate locate# :speed speed# :cd cd#}))

(defmacron draw-items! []
  `(loop [items# @a-items]
     (when-not (empty? items#)
       (let [item# (first items#)
             id# (:id item#)
             ^Vector3 locate# (:locate item#)
             ^Texture tex# (id# @a-items-tex)
             tex-width# (.getWidth tex#)
             tex-height# (.getHeight tex#)
             ]
         (.draw ^SpriteBatch (batch)
                tex#
                (.x locate#)
                (.y locate#)
                (float (/ tex-width# 2))
                (float (/ tex-height# 2))
                (float tex-width#)
                (float tex-height#)
                (float 1)
                (float 1)
                (.z locate#)
                (float 0)
                (float 0)
                (float tex-width#)
                (float tex-height#)
                false
                false)
         (recur (rest items#))))))

(defmacron process-item! []
  `(let [delta# (get-delta)
         next-timer# @a-items-next-spawn-nsec
         now# (TimeUtils/nanoTime)
         spawn?# (< next-timer# now#)
         volume-off?# (pref :volume-off?)
         item-delete-left# (.x items-delete-screen-l-b-r)
         item-delete-bottom# (.y items-delete-screen-l-b-r)
         item-delete-right# (.z items-delete-screen-l-b-r)
         r# (loop [items# @a-items result# nil]
              (if (empty? items#)
                result#
                (recur
                  (rest items#)
                  (let [item# (first items#)
                        {id# :id
                         ^Vector3 locate# :locate
                         ^Vector3 speed# :speed
                         ^Rectangle cd# :cd} item#
                        x-locate# (.x locate#)
                        y-locate# (.y locate#)
                        z-locate# (.z locate#)
                        x-speed# (.x speed#)
                        y-speed# (.y speed#)
                        z-speed# (.z speed#)
                        ]
                    ;; item-collision
                    (if (.overlaps cd# player-hit-rect)
                      (do
                        ;; get-item
                        (inc-score! id#)
                        (when-not volume-off?# (.play ^Sound (id# @a-items-se)))
                        result#)
                      (cond ; check item erase
                        (< y-locate# item-delete-bottom#) result#
                        (and
                          (< x-speed# 0)
                          (< x-locate# item-delete-left#)) result#
                        (and
                          (< 0 x-speed#)
                          (< item-delete-right# x-locate#)) result#
                        :else (let [delta-x# (* delta# x-speed#)
                                    delta-y# (* delta# y-speed#)
                                    delta-z# (* delta# z-speed#)]
                                ;; update locate
                                (.set locate#
                                      (+ x-locate# delta-x#)
                                      (+ y-locate# delta-y#)
                                      (mod (+ z-locate# delta-z#) 360))
                                (set! (.x cd#) (+ (.x cd#) delta-x#))
                                (set! (.y cd#) (+ (.y cd#) delta-y#))
                                (cons item# result#))))))))
         r2# (reverse r#)
         r3# (if spawn?# (cons (item-spawn) r2#) r2#)]
     (when spawn?#
       (reset! a-items-next-spawn-nsec
               (+ now# (rand (/ item-spawn-interval speed-level)))))
     (reset! a-items r3#)))


;; ----------------------------------------------------------------
;; *** background ***
(def a-background-star-tex (atom nil))
(def a-background-stars (atom nil))
(def a-background-star-next-spawn-nsec (atom 0))
(def a-star-dist (atom 0))
(def ^Vector2 pt-center (Vector2.))

(defn init-background! []
  (let [width 1
        height 1
        pm (doto (Pixmap. width height Pixmap$Format/RGBA8888)
             (.setColor (Color. 1 1 1 1))
             (.fill)
             )
        tex (Texture. pm)
        ]
    (.dispose pm)
    (register-disposer! tex)
    (reset! a-background-star-tex tex)))

(defn update-background-info! []
  (.set pt-center
        (float (/ (get-screen-width) 2))
        (float (/ (get-screen-height) 2)))
  (reset! a-star-dist (/ (+ (get-screen-width) (get-screen-height)) 2)))

(defmacron distance->transparency [dist] `(Math/atan (/ ~dist 100)))

(defmacron draw-background! []
  `(let [^SpriteBatch b# (batch)]
     (loop [stars# @a-background-stars]
       (when-not (empty? stars#)
         (let [{a# :angle dist# :distance tp# :tp-cur} (first stars#)
               v# (doto (Vector2. 1 0) (.setAngle a#) (.mul (float dist#)))
               x# (+ (.x pt-center) (.x v#))
               y# (+ (.y pt-center) (.y v#))]
           (.setColor b# (float 1) (float 1) (float 1) (float tp#))
           (.draw b# ^Texture @a-background-star-tex (float x#) (float y#))
           (recur (rest stars#)))))
     (.setColor b# (float 1) (float 1) (float 1) (float 1))))

(defmacron star-spawn []
  `(let [tp# (+ 0.3 (rand))]
     {:angle (rand 360)
      :distance @a-star-dist
      :tp-orig tp#
      :tp-cur (if (< 1 tp#) 1 tp#)
      :speed-level (+ 0.98 (rand 0.01))}))

(defmacron process-background! []
  `(let [screen-width# (get-screen-width)
         screen-height# (get-screen-height)
         interval# (get-delta)
         result# (loop [stars# @a-background-stars rtmp# nil]
                   (if (empty? stars#)
                     rtmp#
                     (recur
                       (rest stars#)
                       (let [star-info# (first stars#)
                             {a# :angle dist# :distance
                              tp# :tp-orig s-l# :speed-level} star-info#
                             new-dist# (* dist# (Math/pow s-l# interval#))
                             d-tp# (distance->transparency dist#)
                             tp-merge# (* tp# d-tp#)
                             tp-lpf# (if (< 1 tp-merge#) 1 tp-merge#)
                             ]
                         (if (< tp-lpf# 0.1)
                           rtmp#
                           (cons (merge star-info# {:distance new-dist#
                                                    :tp-cur tp-lpf#})
                                 rtmp#))))))]
     (reset! a-background-stars result#)
     (let [next-timer# @a-background-star-next-spawn-nsec
           now# (TimeUtils/nanoTime)]
       (when (< next-timer# now#)
         (reset! a-background-star-next-spawn-nsec
                 (+ now# (rand (/ star-spawn-interval speed-level))))
         (swap! a-background-stars conj (star-spawn))))))


;; ----------------------------------------------------------------
;; *** simple-console ***
(def a-simple-console-timer (atom 0))
(def a-heap-record (atom ""))
(def ^:dynamic simple-console-draw-level)
(def a-console-cache-map (atom {}))
(def a-console-cache-loc (atom {}))
(def ^:const margin-x 2)
(def ^:const margin-y 2)
(def color-debug (Color. 1 0 0 0.5))
(def color-console (Color. 1 1 1 0.2))

(defmacro when-debug [& bodies] (if Info/debug `(do ~@bodies) nil))

(defmacron register-cache-map! [k text x y color]
  `(let [k# ~k
         x# ~x
         y# ~y
         ^Color c# ~color
         entry# {k# (doto ^BitmapFontCache (BitmapFontCache. (font))
                      (.setText ~text x# y#)
                      (.setColor c#))}]
     (swap! a-console-cache-map merge entry#)
     (swap! a-console-cache-loc merge {k# (Vector2. x# y#)})))

(defmacron register-cache-map-2! [k text color]
  `(do
     (register-cache-map! ~k ~text margin-x simple-console-draw-level ~color)
     (set! simple-console-draw-level
           (- simple-console-draw-level @a-font-line-height))))

(defn update-simple-console! []
  (binding [simple-console-draw-level (- (get-screen-height) 1 margin-y)]
    (when-debug
      (register-cache-map-2!
        :debug (str "DEBUG: " Info/projectVersion) color-debug))
    (when-debug
      (register-cache-map-2!
        :build-date (str "BDT: " Info/buildDate) color-console))
    (register-cache-map-2!
      :build-num (str "BNM: " Info/buildNumber) color-console)
    (when-debug
      (register-cache-map-2!
        :os-type (str "SYS: " (.. Gdx app (getType))) color-console))
    (when-debug
      (register-cache-map-2!
        :sdk-ver (str "SDK: " (.. Gdx app (getVersion))) color-console))
    (register-cache-map-2! :fps "FPS: " color-console)
    (register-cache-map-2! :heap "MEM: " color-console)
    ;; debug print here
    (when-debug
      (register-cache-map-2! :items "ITEMS: " color-console))
    (when-debug
      (register-cache-map-2! :stars "BG-STARS: " color-console))
    ))

(defmacron update-cache! [k text]
  `(let [k# ~k
         ^BitmapFontCache cache# (k# @a-console-cache-map)
         ^Vector2 loc# (k# @a-console-cache-loc)]
     (when cache#
       (.setText cache# ~text (.x loc#) (.y loc#)))))

(defmacron process-simple-console! []
  `(let [now# (TimeUtils/nanoTime)]
     (when (< @a-simple-console-timer now#)
       (reset! a-simple-console-timer (+ now# console-update-interval-nsec))
       (reset! a-heap-record (format "%09d" (.. Gdx app (getNativeHeap))))
       (update-cache! :fps (str "FPS: " (.. Gdx graphics (getFramesPerSecond))))
       (update-cache! :heap (str "MEM: " @a-heap-record))
       (update-cache! :items (str "ITEMS: " (count @a-items)))
       (update-cache! :stars (str "BG-STARS: " (count @a-background-stars)))
       )))

(defmacron draw-cache-if-exists [cache]
  `(let [^BitmapFontCache cache# ~cache] (when cache# (.draw cache# (batch)))))

(defmacron draw-simple-console! []
  `(loop [^BitmapFontCache cm# @a-console-cache-map]
     (when-not (empty? cm#)
       (draw-cache-if-exists (second (first cm#)))
       (recur (rest cm#)))))


;; ----------------------------------------------------------------
;; *** eval-console ***
(def a-eval-console-status (atom nil))
(def a-eval-console-future (atom nil))
(def a-eval-console-text (atom "press 'E' key to eval file 'eval.clj'."))
(def eval-console-color (Color. 1 1 1 1))
(def eval-console-x 2)
(def a-eval-console-y (atom player-locate-y))
(def a-eval-console-fc (atom nil))

(defn init-eval-console! []
  (when Info/debug
    (reset! a-eval-console-status :none)
    (reset! a-eval-console-y (+ 2 @a-font-line-height))
    (reset! a-eval-console-fc (BitmapFontCache. (font)))
    ))

(defn update-eval-console-text! []
  (when Info/debug
    (doto ^BitmapFontCache @a-eval-console-fc
      (.setText @a-eval-console-text eval-console-x @a-eval-console-y)
      (.setColor ^Color eval-console-color))))

(defmacron pe-none! []
  `(when (.. Gdx input (isKeyPressed Input$Keys/E))
     (let [path# (eval '@#'jp.ne.tir.drop.al/eval-path)]
       (reset! a-eval-console-text "( loading file ... )")
       (update-eval-console-text!)
       (try
         (let [f# (future (try (slurp path#) (catch Exception e# e#)))]
           (reset! a-eval-console-future f#)
           (reset! a-eval-console-status :load))
         (catch RejectedExecutionException e#
           (reset! a-eval-console-text
                   "( cannot get thread, retry loading file ... )")
           (update-eval-console-text!))))))
(defmacron pe-load! []
  `(when (future-done? @a-eval-console-future)
     (let [result# @@a-eval-console-future]
       (if (string? result#)
         (do
           (reset! a-eval-console-text "( loaded. eval ... )")
           (update-eval-console-text!)
           (reset! a-eval-console-status :eval))
         (do
           (reset! a-eval-console-text (str "LOAD-ERROR: " result#))
           (update-eval-console-text!)
           (reset! a-eval-console-status :done))))))

(defmacron pe-eval! []
  `(do
     (try
       (let [r# (binding [*ns* (find-ns 'jp.ne.tir.drop.drop)]
                  (load-string @@a-eval-console-future))
             r-size# (count (str r#))
             r2# (if (< 255 r-size#) (subs r# 0 r-size#) r#)]
         (reset! a-eval-console-text (str "OK: " r2#)))
       (catch Exception e#
         (reset! a-eval-console-text (str "EVAL-ERROR: " e#))))
     (update-eval-console-text!)
     (reset! a-eval-console-status :done)))

(defmacron pe-done! []
  ;; it is prevent to repeat key
  `(when-not (.. Gdx input (isKeyPressed Input$Keys/E))
     (reset! a-eval-console-status :none)))

(defmacron process-eval-console! []
  (when Info/debug
    `(case @a-eval-console-status
       :none (pe-none!)
       :load (pe-load!)
       :eval (pe-eval!)
       :done (pe-done!)
       (throw (RuntimeException. (str "no match status: "
                                      @a-eval-console-status))))))

(defmacron draw-eval-console! []
  (when Info/debug
    `(.draw ^BitmapFontCache @a-eval-console-fc (batch))))


;; ----------------------------------------------------------------
;; *** process draw ***
(defn process-draw! []
  (.. Gdx gl (glClearColor 0.0 0.0 0.1 1.0)) ; R G B A
  (.. Gdx gl (glClear (. GL10 GL_COLOR_BUFFER_BIT)))
  (with-batch
    (draw-background!)
    (draw-player!)
    (draw-items!)
    (draw-volume-button!)
    (draw-score!)
    (draw-simple-console!)
    (draw-eval-console!)
    ))


;; ----------------------------------------------------------------
;; *** main ***


(defn drop-create []
  (when do-prof? (println "!!! do-prof? is true (slow) !!!"))
  (when macron-is-fn? (println "!!! macron-is-fn? is true (slow) !!!"))
  (load-pref-from-storage!)
  (init-music!)
  (init-batch!)
  (init-camera!)
  (init-font!)
  (init-score!)
  (init-volume-button!)
  (init-player!)
  (init-items!)
  (init-background!)
  (init-eval-console!)
  (when do-prof? (eval '(android.os.Debug/startMethodTracing "drop"))))


(defn drop-resize [w-orig h-orig]
  (let [w (clamp-num (first min-screen-size) w-orig (first max-screen-size))
        h (clamp-num (second min-screen-size) h-orig (second max-screen-size))]
    (update-screen-rect! w h)
    (.setToOrtho ^OrthographicCamera (camera) false w h)
    (.update ^Camera (camera))
    (.setProjectionMatrix ^SpriteBatch (batch)
                          (.combined ^OrthographicCamera (camera)))
    (update-volume-button-rect!)
    (update-player-max-x!)
    (clamp-player-locate-x!)
    (update-all-score!)
    (update-items-delete-rect!)
    (update-background-info!)
    (update-simple-console!)
    (update-eval-console-text!)
    ))


(defn drop-render []
  (process-draw!)
  (let [just-touched (.. Gdx input (justTouched))
        is-touched (or just-touched (.. Gdx input (isTouched)))
        ^Vector3 pos (and is-touched (get-touch-pos))
        touch-x (and is-touched (.x pos))
        touch-y (and is-touched (.y pos))
        ]
    (process-volume-button! just-touched touch-x touch-y)
    (process-player! is-touched touch-x touch-y)
    (process-item!)
    (process-background!)
    (process-simple-console!)
    (process-eval-console!)
    ))


(defn drop-pause []
  (save-pref-to-storage!))


(defn drop-resume []
  ;; dynamic-generated-texture was reset by pause->resume,
  ;; that must be reconstruct.
  (init-volume-button!)
  (init-background!)
  )


(defn drop-dispose []
  (when do-prof? (eval '(android.os.Debug/startMethodTracing)))
  (shutdown-agents)
  (dispose-all!))


