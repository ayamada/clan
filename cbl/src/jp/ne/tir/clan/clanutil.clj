(ns jp.ne.tir.clan.clanutil
  (:import
    (com.badlogic.gdx Gdx Application$ApplicationType ApplicationListener)
    )
  (:use
    [clj-time.local :only [local-now]]
    [clj-time.format :only [unparse formatter-local]]
    )
  ;(:gen-class
  ;  :name jp.ne.tir.clan.clanutil
  ;  :methods [#^{:static true} [genCal [String] ApplicationListener]]
  )

;;; ----------------------------------------------------------------
;;; common utilities

;; コンパイル時に値を固定する為のマクロ。必要なら適切にquoteした結果を返す事
;; (def a (eager (str "aa" "bb"))) => (def a "aabb")
(defmacro eager [expr] (eval expr))


;;; ----------------------------------------------------------------
;;; dynamic compile-time parameters

(def ^:const clan-info-build-target ; (or :desktop :android ...)
  (eager
    (let [build-target (or (System/getenv "CLAN_TARGET") "desktop")
          k (case (keyword build-target)
              :desktop :desktop
              :android :android
              nil)]
      (assert k build-target)
      k)))
(def ^:const clan-info-release?
  (eager (boolean (System/getenv "CLAN_RELEASE"))))
(def ^:const clan-info-build-number
  ;; TODO: must be include more information
  (eager (str (System/currentTimeMillis))))
(def ^:const clan-info-build-date
  ;; http://docs.oracle.com/javase/6/docs/api/java/text/SimpleDateFormat.html
  (eager
    (unparse (formatter-local "yyyy/MM/dd HH:mm:ss z") (local-now))))
;; TODO: ビルド環境/ビルドには外部コマンド実行が必要だが、
;; clojure.java.shell を使うと shutdown-agents の実行が必要になってしまう。
;; なので後回しとする。
;; see http://clojuredocs.org/clojure_core/clojure.java.shell/sh
(def ^:const clan-info-build-env
  (eager "sorry, not implemented yet"))
(def ^:const clan-info-build-javac-version
  (eager "sorry, not implemented yet"))


;;; ----------------------------------------------------------------
;;; dynamic compile-time parameter utilities

(defmacro keep-code-when-release [& bodies]
  (when clan-info-release? `(do ~@bodies)))
(defmacro purge-code-when-release [& bodies]
  (when-not clan-info-release? `(do ~@bodies)))
(defmacro if-release [then else]
  (if clan-info-release? then else))

(defmacro keep-code-when-desktop [& bodies]
  (when (= :desktop clan-info-build-target) `(do ~@bodies)))
(defmacro purge-code-when-desktop [& bodies]
  (when-not (= :desktop clan-info-build-target) `(do ~@bodies)))
(defmacro if-desktop [then else]
  (if (= :desktop clan-info-build-target) then else))

(defmacro keep-code-when-android [& bodies]
  (when (= :android clan-info-build-target) `(do ~@bodies)))
(defmacro purge-code-when-android [& bodies]
  (when-not (= :android clan-info-build-target) `(do ~@bodies)))
(defmacro if-android [then else]
  (if (= :android clan-info-build-target) then else))


;;; ----------------------------------------------------------------
;;; BootLoader.java utitities

;; android実機にて、次回起動時にまたブートロゴを出すフラグを設定する
(defmacro set-display-bootlogo-in-android! []
  `(set! (. jp.ne.tir.clan.ALC reserveNextAlClear) true))

;; BootLoader.javaがcalを取り出す為に使う
(defn generate-cal [class-str fn-str]
  (let [class-symbol (symbol class-str)
        fn-symbol (symbol (str class-str "/" fn-str))]
    (eval `(do (require '~class-symbol) (~fn-symbol)))
    ))


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

