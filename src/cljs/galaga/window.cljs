(ns galaga.window
  (:require [cljs.core.async :as async
             :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def canvas (.getElementById js/document "world"))
(def context (.getContext canvas "2d"))

(def cell-size 30)
(def empty-cell-color "#eee")
(def player-cell-color "666")
(def enemy-cell-color "333")
(def border-color "#cdcdcd")
(def projectile-cell-color "#aaa")

(defn- make-odd [num]
  (if (= (mod num 2) 0)
    (dec num)
    num))

(defn- fill-square [x y color]
  (set! (.-fillStyle context) color)
  (set! (.-strokeStyle context) border-color)
  (.fillRect context
             (* x cell-size)
             (* y cell-size)
             cell-size
             cell-size)
  (.strokeRect context
               (* x cell-size)
               (* y cell-size)
               cell-size
               cell-size))

(defn- fill-empty [env]
  (let [[height width] (env :dimensions)]
    (doseq [y (range height)
            x (range width)]
      (fill-square x y empty-cell-color))))

(defn draw-loop [draw]
  (go
   (loop []
     (let [env (<! draw)]
       (fill-empty env)
       (let [[x y] (env :player)]
         (fill-square x y player-cell-color))
       (doseq [[x y] (map :coords (env :enemies))]
         (fill-square x y enemy-cell-color))
       (doseq [[x y] (env :projectiles)]
         (fill-square x y projectile-cell-color)))
     (recur))))

(defn init [draw-chan]
  (set! (.-width canvas) (- (.-innerWidth js/window) cell-size))
  (set! (.-height canvas) (- (.-innerHeight js/window) cell-size))
  (draw-loop draw-chan)
  (let [height (-> (/ (.-height canvas) cell-size) .toFixed int)
        width (-> (/ (.-width canvas) cell-size) .toFixed int)]
    {:dimensions (map make-odd [height width])}))
