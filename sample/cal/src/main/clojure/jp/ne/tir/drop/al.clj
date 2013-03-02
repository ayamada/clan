(ns jp.ne.tir.drop.al
  (:import
    (com.badlogic.gdx Gdx ApplicationListener)
    (jp.ne.tir.clan Info)
    )
  (:use
    [jp.ne.tir.drop.drop :only [drop-create drop-resize drop-render 
                                drop-pause drop-resume drop-dispose]]
    )
  (:gen-class
    :implements [com.badlogic.gdx.ApplicationListener]
    ;:init init
    ;:state state
    ))

;(defn -init []
;  [[] (atom {})])
;; you can (reset! (.state this) {...}) and (:foo @(.state this)) if need


;; this path is autoloaded at booted on debug-time.
;; !!! YOU MUST CHANGE FOR DEBUG !!!
(def ^:const drop-path
  (if Info/debug "path/to/drop.clj" nil))
(def ^:const eval-path
  (if Info/debug "path/to/eval.clj" nil))
;; it for ayamada
;(def ^:const drop-path
;  (if Info/debug "http://misc.tir.jp/proxy.cgi/drop.clj" nil))
;(def ^:const eval-path
;  (if Info/debug "http://misc.tir.jp/proxy.cgi/eval.clj" nil))

(defn autoload-drop-path! []
  (when drop-path
    (let [src (try
                (slurp drop-path)
                (catch Exception e (.printStackTrace e) nil))]
      (when src (load-string src)))))




;; you must implement these six methods.

(defn -create [this]
  (autoload-drop-path!)
  (drop-create))

(defn -resize [this width height]
  (drop-resize width height))

(defn -render [this]
  (drop-render))

(defn -pause [this]
  (drop-pause))

(defn -resume [this]
  (drop-resume))

(defn -dispose [this]
  (drop-dispose))


