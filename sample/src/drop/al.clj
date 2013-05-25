(ns drop.al
  (:import
    (com.badlogic.gdx Gdx ApplicationListener)
    ;(jp.ne.tir.clan Info)
    )
  (:use
    [drop.main :only [drop-create drop-resize drop-render 
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



;; you must implement these six methods.

(defn -create [this]
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


