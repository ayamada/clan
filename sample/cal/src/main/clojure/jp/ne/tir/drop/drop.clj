(ns jp.ne.tir.drop.drop
  (:import
    (jp.ne.tir.clan Info)
    (com.badlogic.gdx Gdx Preferences Input$Keys Net
                      Application$ApplicationType)
    (com.badlogic.gdx.audio Music Sound)
    (com.badlogic.gdx.files FileHandle)
    (com.badlogic.gdx.graphics GL10 Camera OrthographicCamera Texture Color
                               Pixmap Pixmap$Format)
    (com.badlogic.gdx.graphics.g2d SpriteBatch BitmapFont BitmapFontCache
                                   NinePatch)
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
(def ^:const definline-is-fn? false) ; for stacktrace

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
;; definline as fn for profiler, cannot use & in args
(defmacro definline- [mname & mdecl]
  (if definline-is-fn?
    (let [tmp-mname (gensym mname)]
      `(do
         (definline ~tmp-mname ~@mdecl)
         (defn ~mname [& args#] (apply ~tmp-mname args#))))
    `(definline ~mname ~@mdecl)))

;; replacement of dorun
(defmacro do-each [targets [match-arg] & bodies]
  `(loop [left# ~targets]
     (when-not (empty? left#)
       (let [~match-arg (first left#)]
         ~@bodies
         (recur (rest left#))))))

;; all asset files are in "assets/ASSETS-DIR/" for safety.
(defn assets-file ^FileHandle [path]
  (.. Gdx files (internal (str assets-dir "/" path))))

(definline- get-delta [] `(* (.. Gdx graphics (getDeltaTime)) speed-level))

(definline- clamp-num [min-n n max-n]
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

(definline- pref [kwd] `^Preferences(~kwd @a-prefs))

(definline- pref! [kwd new-val] `(swap! a-prefs merge {~kwd ~new-val}))

(definline- get-gdx-pref [] `(.. Gdx app (getPreferences pref-name)))

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
  (doto (.. Gdx app (getPreferences (str "CBL-" Info/projectGroupId
                                         "-" Info/projectArtifactId
                                         "-" Info/projectClassifier)))
    (.putBoolean "PLAY_JINGLE" on-off)
    (.flush)) ; see BootLoader.java
  (pref! :volume-off? (not on-off))
  (if on-off (.play ^Music @a-music) (.stop ^Music @a-music)))


;; ----------------------------------------------------------------
;; *** gdx's batch ***
(def a-batch (atom nil))

(definline- batch [] `^SpriteBatch@a-batch)

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
(def ^Vector3 touch-pos (Vector3.))

(definline- camera [] `^OrthographicCamera@a-camera)

(defn init-camera! [] (reset! a-camera (OrthographicCamera.)))

(definline- get-screen-width [] `(.x screen-rect))
(definline- get-screen-height [] `(.y screen-rect))
(definline- update-screen-rect! [w h]
  `(do (set! (.x screen-rect) ~w) (set! (.y screen-rect) ~h)))

(definline- update-touch-pos! []
  `(do
     (.. touch-pos (set (.. Gdx input (getX)) (.. Gdx input (getY)) 0))
     (.. ^OrthographicCamera (camera) (unproject touch-pos))
     nil))


;; ----------------------------------------------------------------
;; *** gdx's font ***
(def a-font (atom nil))
(def a-font-line-height (atom nil))

(definline- font [] `^BitmapFont@a-font)

(defn init-font! []
  (let [f (BitmapFont.)]
    ;(.setFixedWidthGlyphs f (apply str (map char (range 32 127))))
    (.setFixedWidthGlyphs f "0123456789")
    (reset! a-font-line-height (.getLineHeight f))
    (register-disposer! f)
    (reset! a-font f)))


;; ----------------------------------------------------------------
;; *** dialog (by NinePatch) ***
(def a-dialog-tex (atom nil))
(def a-dialog-np (atom nil))
(def a-dialog-nothing? (atom true))
(def a-dialog-title (atom ""))
(def a-dialog-url (atom ""))
(def ^Rectangle dialog-url-rect (Rectangle.))
(def ^Rectangle dialog-close-rect (Rectangle.))
(def ^:const dialog-close-label "[CLOSE]")

(defn init-dialog! []
  (let [tex (Texture. (assets-file "dialog.png"))
        np (NinePatch. tex 16 16 16 16)
        ]
    (register-disposer! tex)
    (reset! a-dialog-tex tex)
    (reset! a-dialog-np np)))

(defn open-dialog! [title url]
  (reset! a-dialog-nothing? false)
  (reset! a-dialog-title title)
  (reset! a-dialog-url url))

(defn draw-dialog! []
  (when-not @a-dialog-nothing?
    (let [^NinePatch np @a-dialog-np
          url @a-dialog-url
          sc-w (get-screen-width)
          sc-h (get-screen-height)
          np-w 256
          np-h 128
          np-x (/ (- sc-w np-w) 2)
          np-y (/ (- sc-h np-h) 2)
          line-height @a-font-line-height
          label-x (+ np-x (.getPadLeft np))
          label-y (- (+ np-y np-h) (.getPadTop np))
          close-w (.width (.getBounds ^BitmapFont (font) dialog-close-label))
          close-h line-height
          close-x (- (+ np-x np-w) (.getPadRight np) close-w)
          close-y label-y
          np-inner-w (- np-w (.getPadLeft np) (.getPadRight np))
          url-b (.getWrappedBounds ^BitmapFont (font) url np-inner-w)
          url-w (.width url-b)
          url-h (.height url-b)
          url-x (/ (- sc-w url-w) 2)
          url-y (+ np-y (.getPadBottom np) url-h)
          ]
      (.set dialog-close-rect
            close-x (- close-y close-h 8) close-w (+ close-h 16))
      (.set dialog-url-rect url-x (- url-y url-h 8) url-w (+ url-h 16))
      (.draw np (batch) np-x np-y np-w np-h)
      (.setColor ^BitmapFont (font) Color/BLACK)
      (.draw ^BitmapFont (font) (batch) @a-dialog-title label-x label-y)
      (.setColor ^BitmapFont (font) Color/BLUE)
      (.draw ^BitmapFont (font) (batch) dialog-close-label close-x close-y)
      (.drawWrapped ^BitmapFont (font) (batch) url url-x url-y np-inner-w))))

(defn process-dialog! [x y]
  (let [pressed-url? (.contains ^Rectangle dialog-url-rect x y)
        pressed-close? (.contains ^Rectangle dialog-close-rect x y)]
    (when pressed-url?
      (.openURI ^Net (.. Gdx app (getNet)) @a-dialog-url))
    (when pressed-close?
      (reset! a-dialog-nothing? true))))


; ----------------------------------------------------------------
;; *** generalized button ***
(def a-buttons (atom nil))

(defn spawn-button [{k :key init :init update :update
                     pause :pause resume :resume just-touch :just-touch}]
  {:key k ; button identifier
   :init init ; (fn [button] ...) or identity
   :update update ; (fn [button width height] ...) or identity
   :pause pause ; (fn [button] ...) or identity
   :resume resume ; (fn [button] ...) or identity
   :just-touch just-touch ; (fn [button] ...) or identity
   :a-on-tex (atom nil) ; (Texture.)
   :a-off-tex (atom nil) ; (Texture.)
   :rect (Rectangle.)
   :a-on-off (atom false)
   })

(defn unregister-all-button! []
  (reset! a-buttons nil))

(defn register-button! [m]
  (swap! a-buttons conj (spawn-button m)))

(defn init-buttons! []
  (do-each (reverse @a-buttons) [button] ((:init button) button)))

(defn update-buttons! [w h]
  (do-each (reverse @a-buttons) [button] ((:update button) button w h)))

(defn pause-buttons! []
  (do-each (reverse @a-buttons) [button] ((:pause button) button)))

(defn resume-buttons! []
  (do-each (reverse @a-buttons) [button] ((:resume button) button)))

(definline- process-buttons! [just-touched? touch-x touch-y]
  `(when ~just-touched?
     (let [x# ~touch-x y# ~touch-y]
       (do-each
         @a-buttons
         [button#]
         (when (.contains ^Rectangle (:rect button#) x# y#)
           ((:just-touch button#) button#))))))

(definline- draw-buttons! []
  `(do-each
     @a-buttons
     [b#]
     (let [^Rectangle r# (:rect b#)
           ^Texture t# (if @(:a-on-off b#) @(:a-on-tex b#) @(:a-off-tex b#))]
       (.draw ^SpriteBatch (batch) t# (.x r#) (.y r#)))))

(definline- get-button [k]
  `(loop [left# @a-buttons]
     (when-not (empty? left#)
       (let [b# (first left#)]
         (if (= ~k (:key b#)) b# (recur (rest left#)))))))

(definline- get-button-rect [k]
  `(let [b# (get-button ~k)] (when b# ^Rectangle (:rect b#))))


; ----------------------------------------------------------------
;; *** volume button ***
(def ^:const VOLUME-BUTTON-WIDTH 64)
(def ^:const VOLUME-BUTTON-HEIGHT 64)

(defn init-volume-button! [button]
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
    (reset! (:a-on-off button) (not (pref :volume-off?)))
    (reset! (:a-on-tex button) on-tex)
    (reset! (:a-off-tex button) off-tex)))

(defn dispose-volume-button! [button]
  (let [on @(:a-on-tex button) off @(:a-off-tex button)]
    (when on (.dispose ^Texture on) (reset! (:a-on-tex button) nil))
    (when off (.dispose ^Texture off) (reset! (:a-off-tex button) nil))))

(defn update-volume-button-rect! [button screen-w screen-h]
  ;; it set to corner of up-right
  (let [w VOLUME-BUTTON-WIDTH
        h VOLUME-BUTTON-HEIGHT
        x (- screen-w w 2)
        y (- screen-h h 2)]
    (.set ^Rectangle (:rect button) (float x) (float y) (float w) (float h))))

(defn process-volume-button! [button]
  (swap! (:a-on-off button) not)
  (change-volume! @(:a-on-off button)))

(defn register-volume-button! []
  (register-button!
    {:key :volume
     :init init-volume-button!
     :update update-volume-button-rect!
     :pause dispose-volume-button!
     :resume init-volume-button!
     :just-touch process-volume-button!
     }))


; ----------------------------------------------------------------
;; *** clan button ***
(def ^:const clan-url "https://github.com/ayamada/clan")

(defn init-clan-button! [button]
  (let [tex (Texture. (assets-file "clan.png"))]
    (register-disposer! tex)
    (reset! (:a-on-tex button) tex)
    (reset! (:a-off-tex button) tex)))

(defn update-clan-button-rect! [button screen-w screen-h]
  ;; it set to corner of up-right
  (let [^Texture tex @(:a-on-tex button)
        w (.getWidth tex)
        h (.getHeight tex)
        x (- (.x ^Rectangle (get-button-rect :volume)) w)
        y (- screen-h h 2)]
    (.set ^Rectangle (:rect button) (float x) (float y) (float w) (float h))))

(defn process-clan-button! [button]
  (open-dialog! "CLAN" clan-url))

(defn register-clan-button! []
  (register-button!
    {:key :clan
     :init init-clan-button!
     :update update-clan-button-rect!
     :pause identity
     :resume identity
     :just-touch process-clan-button!
     }))


; ----------------------------------------------------------------
;; *** license button ***
(def ^:const url-license-prefix
  "https://github.com/ayamada/clan/raw/0.0.2-EXPERIMENTAL/doc/drop/")
(def ^:const url-license-apk (str url-license-prefix "license_apk.txt"))
(def ^:const url-license-jar (str url-license-prefix "license_jar.txt"))
(def ^:const url-license-exe (str url-license-prefix "license_exe.txt"))

(defn init-license-button! [button]
  (let [tex (Texture. (assets-file "license.png"))]
    (register-disposer! tex)
    (reset! (:a-on-tex button) tex)
    (reset! (:a-off-tex button) tex)))

(defn update-license-button-rect! [button screen-w screen-h]
  ;; it set to corner of up-right
  (let [^Texture tex @(:a-on-tex button)
        w (.getWidth tex)
        h (.getHeight tex)
        x (- (.x ^Rectangle (get-button-rect :clan)) w)
        y (- screen-h h 2)]
    (.set ^Rectangle (:rect button) (float x) (float y) (float w) (float h))))

(defn process-license-button! [button]
  (let [url (cond
              (= Application$ApplicationType/Android
                 (.. Gdx app (getType))) url-license-apk
              (.endsWith ^String (System/getProperty "sun.java.command" "")
                         ".exe") url-license-exe
              :else url-license-jar)]
    (open-dialog! "LICENSE" url)))

(defn register-license-button! []
  (register-button!
    {:key :license
     :init init-license-button!
     :update update-license-button-rect!
     :pause identity
     :resume identity
     :just-touch process-license-button!
     }))


; ----------------------------------------------------------------
;; *** space/drop button ***
(def a-game-mode? (atom true))

(defn init-space-button! [button]
  (let [on-tex (Texture. (assets-file "sd_on.png"))
        off-tex (Texture. (assets-file "sd_off.png"))]
    (register-disposer! on-tex)
    (register-disposer! off-tex)
    (reset! (:a-on-off button) @a-game-mode?)
    (reset! (:a-on-tex button) on-tex)
    (reset! (:a-off-tex button) off-tex)))

(defn update-space-button-rect! [button screen-w screen-h]
  ;; it set to corner of up-right
  (let [^Texture tex @(:a-on-tex button)
        w (.getWidth tex)
        h (.getHeight tex)
        x (- screen-w w 2)
        y (- (.y ^Rectangle (get-button-rect :volume)) h)]
    (.set ^Rectangle (:rect button) (float x) (float y) (float w) (float h))))

(defn process-space-button! [button]
  (let [new-state (not @a-game-mode?)]
    (reset! (:a-on-off button) new-state)
    (reset! a-game-mode? new-state)))

(defn register-space-button! []
  (register-button!
    {:key :space
     :init init-space-button!
     :update update-space-button-rect!
     :pause identity
     :resume identity
     :just-touch process-space-button!
     }))


;; ----------------------------------------------------------------
;; *** player ***
(def a-player-tex (atom nil))
(def a-player-tex-width-half (atom 0))
(def a-player-tex-height (atom 0))
(def a-player-max-x (atom 0))
(def ^Rectangle player-hit-rect (Rectangle.))
(def a-player-homing-level (atom 0))
(def ^:const player-homing-level-max 16)

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

(definline- update-player-max-x! []
  `(reset! a-player-max-x (- (get-screen-width) @a-player-tex-width-half)))
(definline- update-player-locate-x! [x]
  `(set! (. player-hit-rect x) (float ~x)))
(definline- get-player-locate-x [] `(.x player-hit-rect))
(definline- clamp-player-locate-x [x]
  `(let [x# ~x
         min-x# @a-player-tex-width-half
         max-x# @a-player-max-x]
     (cond
       (< x# min-x#) min-x#
       (< max-x# x#) max-x#
       :else x#)))
(definline- clamp-player-locate-x! []
  `(set! (. player-hit-rect x) (clamp-player-locate-x (get-player-locate-x))))

(definline- draw-player! []
  `(.draw ^SpriteBatch (batch)
          ^Texture @a-player-tex
          (float (- (.x player-hit-rect) @a-player-tex-width-half))
          (float (- (.y player-hit-rect) @a-player-tex-height))))

(definline- process-player! [delta prev-touched? is-touched? touch-x touch-y]
  `(do
     ;; move by touch
     (when ~is-touched?
       ;; prevent warp player at just-touched
       (when-not ~prev-touched?
         (reset! a-player-homing-level player-homing-level-max))
       (let [x# ~touch-x y# ~touch-y]
         (update-player-locate-x!
           (clamp-player-locate-x
             ;; prevent warp player at just-touched
             (let [lv# @a-player-homing-level]
               (if (zero? lv#)
                 x#
                 (let [old-x# (.x player-hit-rect)
                       dist# (- x# old-x#)
                       move-dist# (* dist# (/ lv#))
                       new-x# (+ old-x# move-dist#)]
                   (if (or (< old-x# new-x# x#)
                           (< x# new-x# old-x#))
                     (do (reset! a-player-homing-level (- lv# 1)) new-x#)
                     (do (reset! a-player-homing-level 0) x#)))))))))
     ;; move by keyboard
     (let [pressed-l# (.. Gdx input (isKeyPressed Input$Keys/LEFT))
           pressed-r# (.. Gdx input (isKeyPressed Input$Keys/RIGHT))]
       (when (or
               (and pressed-l# (not pressed-r#))
               (and (not pressed-l#) pressed-r#))
         (let [player-x# (get-player-locate-x)
               delta# ~delta
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

(definline- solve-score-info [k]
  `(case ~k
     :score-a [2 a-score-a-cache score-a-color]
     :score-b [1 a-score-b-cache score-b-color]
     :score-c [0 a-score-c-cache score-c-color]))

(definline- update-score! [score-key ^Long score]
  `(let [score-key# ~score-key
         score-str# (.toString ~score)
         [lv# a-cache# color#] (solve-score-info score-key#)
         line-height# @a-font-line-height
         score-width# (.width (.getBounds ^BitmapFont (font) score-str#))
         x# (- (get-screen-width) 8 score-width# (* 48 lv#))
         y# (+ 2 line-height#)]
     (doto ^BitmapFontCache @a-cache#
       (.setText score-str# x# y#)
       (.setColor ^Color color#))))

(defn update-all-score! []
  (update-score! :score-a (pref :score-a))
  (update-score! :score-b (pref :score-b))
  (update-score! :score-c (pref :score-c)))

(definline- inc-score! [item-key]
  `(let [item-desc# (~item-key item-table)
         score-key# (:type item-desc#)
         new-score# (+ 1 (pref score-key#))]
     (pref! score-key# new-score#)
     (update-score! score-key# new-score#)))

(definline- draw-score! []
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

(definline- item-spawn []
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

(definline- draw-items! []
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

(definline- process-item! [delta]
  `(let [delta# ~delta
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
  (let [pm (doto (Pixmap. 1 1 Pixmap$Format/RGBA8888)
             (.setColor (Color. 1 1 1 1))
             (.fill))
        tex (Texture. pm)]
    (.dispose pm)
    (reset! a-background-star-tex tex)))

(defn dispose-background! []
  (when @a-background-star-tex
    (.dispose ^Texture @a-background-star-tex)
    (reset! a-background-star-tex nil)))

(defn update-background-info! []
  (.set pt-center
        (float (/ (get-screen-width) 2))
        (float (/ (get-screen-height) 2)))
  (reset! a-star-dist (/ (+ (get-screen-width) (get-screen-height)) 2)))

(definline- distance->transparency [dist] `(Math/atan (/ ~dist 100)))

(definline- draw-background! []
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

(definline- star-spawn []
  `(let [tp# (+ 0.3 (rand))]
     {:angle (rand 360)
      :distance @a-star-dist
      :tp-orig tp#
      :tp-cur (if (< 1 tp#) 1 tp#)
      :speed-level (+ 0.98 (rand 0.01))}))

(definline- process-background! [delta]
  `(let [screen-width# (get-screen-width)
         screen-height# (get-screen-height)
         interval# ~delta
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

(definline- register-cache-map! [k text x y color]
  `(let [k# ~k
         x# ~x
         y# ~y
         ^Color c# ~color
         entry# {k# (doto ^BitmapFontCache (BitmapFontCache. (font))
                      (.setText ~text x# y#)
                      (.setColor c#))}]
     (swap! a-console-cache-map merge entry#)
     (swap! a-console-cache-loc merge {k# (Vector2. x# y#)})))

(definline- register-cache-map-2! [k text color]
  `(do
     (register-cache-map! ~k ~text margin-x simple-console-draw-level ~color)
     (set! simple-console-draw-level
           (- simple-console-draw-level @a-font-line-height))))

(defn update-simple-console! []
  (binding [simple-console-draw-level (- (get-screen-height) 1 margin-y)]
    (if Info/debug
      (register-cache-map-2!
        :debug (str "DEBUG: " Info/projectVersion) color-debug)
      (register-cache-map-2!
        :ver (str "VER: " Info/projectVersion) color-console))
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

(definline- update-cache! [k text]
  `(let [k# ~k
         ^BitmapFontCache cache# (k# @a-console-cache-map)
         ^Vector2 loc# (k# @a-console-cache-loc)]
     (when cache#
       (.setText cache# ~text (.x loc#) (.y loc#)))))

(definline- process-simple-console! []
  `(let [now# (TimeUtils/nanoTime)]
     (when (< @a-simple-console-timer now#)
       (reset! a-simple-console-timer (+ now# console-update-interval-nsec))
       (reset! a-heap-record (format "%09d" (.. Gdx app (getNativeHeap))))
       (update-cache! :fps (str "FPS: " (.. Gdx graphics (getFramesPerSecond))))
       (update-cache! :heap (str "MEM: " @a-heap-record))
       (when-debug
         (update-cache! :items (str "ITEMS: " (count @a-items)))
         (update-cache! :stars (str "BG-STARS: " (count @a-background-stars))))
       )))

(definline- draw-cache-if-exists [cache]
  `(let [^BitmapFontCache cache# ~cache] (when cache# (.draw cache# (batch)))))

(definline- draw-simple-console! []
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

(definline- pe-none! []
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
(definline- pe-load! []
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

(definline- pe-eval! []
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

(definline- pe-done! []
  ;; it is prevent to repeat key
  `(when-not (.. Gdx input (isKeyPressed Input$Keys/E))
     (reset! a-eval-console-status :none)))

(definline- process-eval-console! []
  (when Info/debug
    `(case @a-eval-console-status
       :none (pe-none!)
       :load (pe-load!)
       :eval (pe-eval!)
       :done (pe-done!)
       (throw (RuntimeException. (str "no match status: "
                                      @a-eval-console-status))))))

(definline- draw-eval-console! []
  (when Info/debug
    `(.draw ^BitmapFontCache @a-eval-console-fc (batch))))


;; ----------------------------------------------------------------
;; *** process dispatch ***
(def a-touched? (atom false))

(defn process-draw! []
  (.. Gdx gl (glClearColor 0.0 0.0 0.1 1.0)) ; R G B A
  (.. Gdx gl (glClear (. GL10 GL_COLOR_BUFFER_BIT)))
  (with-batch
    (draw-background!)
    (when @a-game-mode?
      (draw-player!)
      (draw-items!))
    (draw-buttons!)
    (when @a-game-mode?
      (draw-score!)
      (draw-simple-console!))
    (draw-dialog!)
    (draw-eval-console!)
    ))


;; ----------------------------------------------------------------
;; *** main ***


(defn drop-create []
  (when do-prof? (println "!!! do-prof? is true (slow) !!!"))
  (when definline-is-fn? (println "!!! definline-is-fn? is true (slow) !!!"))
  (load-pref-from-storage!)
  (init-music!)
  (init-batch!)
  (init-camera!)
  (init-font!)
  (init-score!)
  (init-dialog!)
  (unregister-all-button!)
  (register-volume-button!)
  (register-clan-button!)
  (register-license-button!)
  (register-space-button!)
  (init-buttons!)
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
    (update-buttons! w h)
    (update-player-max-x!)
    (clamp-player-locate-x!)
    (update-all-score!)
    (update-items-delete-rect!)
    (update-background-info!)
    (update-simple-console!)
    (update-eval-console-text!)
    ))


(defn drop-pause []
  (save-pref-to-storage!)
  ;; dynamic-generated-texture was reset by pause->resume,
  ;; that must be dispose.
  (pause-buttons!)
  (dispose-background!))


(defn drop-resume []
  ;; dynamic-generated-texture was reset by pause->resume,
  ;; that must be reconstruct.
  (resume-buttons!)
  (init-background!)
  )


(defn drop-render []
  (try
    (process-draw!)
    (update-touch-pos!)
    (let [delta (get-delta)
          prev-touched? @a-touched?
          is-touched? (.. Gdx input (isTouched))
          just-touched? (.. Gdx input (justTouched))
          _ (and (or is-touched? just-touched?) (update-touch-pos!))
          touch-x (.x touch-pos)
          touch-y (.y touch-pos)
          game-mode? @a-game-mode?
          dialog-nothing? @a-dialog-nothing?
          ]
      (when dialog-nothing?
        (process-buttons! just-touched? touch-x touch-y))
      (when (and game-mode? dialog-nothing?)
        (process-player! delta prev-touched? is-touched? touch-x touch-y)
        (process-item! delta))
      (process-background! delta)
      (when game-mode?
        (process-simple-console!))
      (process-eval-console!)
      (when (not= prev-touched? is-touched?)
        (reset! a-touched? is-touched?))
      (when (and just-touched? (not dialog-nothing?))
        (process-dialog! touch-x touch-y)))
    (catch Exception e (drop-pause) (throw e))))


(defn drop-dispose []
  (when do-prof? (eval '(android.os.Debug/startMethodTracing)))
  (shutdown-agents)
  (dispose-all!))


