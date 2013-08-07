(ns jp.ne.tir.clan.clanutil
  (:import
    (com.badlogic.gdx Gdx Preferences
                      Application$ApplicationType ApplicationListener)
    )
  (:use
    [jp.ne.tir.clan.claninfo :as claninfo]
    ))


;;; ----------------------------------------------------------------
;;; dynamic compile-time parameter utilities

(defmacro keep-code-when-release [& bodies]
  (when claninfo/is-release? `(do ~@bodies)))
(defmacro purge-code-when-release [& bodies]
  (when-not claninfo/is-release? `(do ~@bodies)))
(defmacro if-release [then else]
  (if claninfo/is-release? then else))

(defmacro keep-code-when-desktop [& bodies]
  (when (= :desktop claninfo/build-target) `(do ~@bodies)))
(defmacro purge-code-when-desktop [& bodies]
  (when-not (= :desktop claninfo/build-target) `(do ~@bodies)))
(defmacro if-desktop [then else]
  (if (= :desktop claninfo/build-target) then else))

(defmacro keep-code-when-android [& bodies]
  (when (= :android claninfo/build-target) `(do ~@bodies)))
(defmacro purge-code-when-android [& bodies]
  (when-not (= :android claninfo/build-target) `(do ~@bodies)))
(defmacro if-android [then else]
  (if (= :android claninfo/build-target) then else))


(purge-code-when-release (set! *warn-on-reflection* true))

;;; ----------------------------------------------------------------
;;; BootLoader.java utitities

;; android実機にて、次回起動時にまたブートロゴを出すフラグを設定する
(defmacro set-display-bootlogo-in-android! []
  '(eval '(do
            (import '[jp.ne.tir.clan ALC])
            (set! ALC/reserveNextAlClear true))))

;; BootLoader.javaがcalを取り出す為に使う
(defn generate-cal [class-str fn-str]
  (let [class-symbol (symbol class-str)
        fn-symbol (symbol (str class-str "/" fn-str))]
    (eval `(do (require '~class-symbol) (~fn-symbol)))
    ))

(def _a-preferences (atom nil))
(defn _get-pref []
  ;; NB: この内部関数はandroid時にしか呼ばれない
  (or
    @_a-preferences
    (let [pref (.. Gdx app (getPreferences "CBL"))]
      (reset! _a-preferences pref)
      pref)))

(defn _get-path-of-jingle-mute []
  ;; NB: この内部関数はPC時にしか呼ばれない
  (let [jcp (System/getProperty "java.class.path")
        dir (if-release (.. Gdx files (local jcp) (parent) (path)) ".")]
    (str dir "/jingle.mute")))

;; jingle無効のオンオフを取得/セット
(defn is-jingle-off-by-pref? []
  (try
    (if-android
      (.getBoolean ^Preferences (_get-pref) "MUTE_JINGLE" false)
      (.. Gdx files (local (_get-path-of-jingle-mute)) (exists)))
    (catch Exception e (.printStackTrace e) nil)))
(defn set-jingle-off-by-pref! [off?]
  (try
    (if-android
      (let [^Preferences pref (_get-pref)]
        (.putBoolean pref "MUTE_JINGLE" off?)
        (.flush pref))
      (if off?
        (.. Gdx files (local (_get-path-of-jingle-mute)) (writeString "" true))
        (.. Gdx files (local (_get-path-of-jingle-mute)) (delete))))
    (catch Exception e (.printStackTrace e) nil)))

;;; ----------------------------------------------------------------
;;; libgdx utilities
;;; TODO: support to iOS and GWT

(defn get-package-type []
  (cond
    (= Application$ApplicationType/Android (.. Gdx app (getType))) :apk
    (.endsWith ^String (System/getProperty "java.class.path" "") ".exe") :exe
    :else :jar))

(defn get-os-type []
  (let [file-separator (System/getProperty "file.separator")]
    (cond
      (= Application$ApplicationType/Android (.. Gdx app (getType))) :android
      (= "\\" file-separator) :windows
      (= "/" file-separator) :unix
      :else :unknown)))


;;; ----------------------------------------------------------------

