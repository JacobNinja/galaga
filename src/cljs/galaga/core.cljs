(ns galaga.core
  (:require [cljs.core.async :as async
             :refer [<! >! chan timeout alts!]]
            [clojure.browser.event :as event]
            [goog.events.KeyHandler :as key-handler]
            [goog.events.KeyCodes :as key-codes]
            [galaga.window]
            [clojure.set])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def keyboard (goog.events.KeyHandler. js/document))
(def keyboard-chan (chan 1))
(def valid-directions (set [key-codes/LEFT key-codes/RIGHT]))
(def valid-input (into #{} (cons key-codes/SPACE valid-directions)))

(defn- keyboard-listen []
  (event/listen keyboard "key" 
                (fn [k]
                  (let [key-code (.-keyCode k)
                        valid-key (valid-input key-code)]
                    (when valid-key
                      (go (>! keyboard-chan valid-key)))))))

(defn- adjust-direction [direction env]
  (assoc env :direction (valid-directions direction)))

(defn- adjust-player-coords [env]
  (let [[x y] (first (env :player))]
    (assoc env :player (cons (cond
                              (= key-codes/LEFT (env :direction)) [(dec x) y]
                              (= key-codes/RIGHT (env :direction)) [(inc x) y]
                              :else [x y])
                             (rest (env :player))))))

(defn- fire [keyboard-input env]
  (if (= keyboard-input key-codes/SPACE)
    (assoc env :projectiles (cons (first (env :player)) (env :projectiles)))
    env))

(defn- adjust-projectile-coords [env]
  (assoc env :projectiles 
         (filter (fn [[x y]] (>= y 0)) 
                 (map (fn [[x y]] [x (dec y)]) (env :projectiles)))))

(defn init-env [env]
  (let [[height width] (env :dimensions)]
    (assoc env :player 
           [[(-> (/ width 2) .toFixed int) (dec height)]]))) 

(defn game-loop [draw env]
  (go
   (>! draw env)
   (loop [env (assoc env :direction (<! keyboard-chan))]
     (let [[keyboard-check _] (alts! [keyboard-chan (timeout 1)])
           next-env (->> env
                         (fire keyboard-check)
                         (adjust-direction keyboard-check)
                         adjust-player-coords
                         adjust-projectile-coords)]
       (>! draw next-env)
       (<! (timeout 500))
       (recur next-env)))))

(defn ^:export init []
  (let [draw (chan)
        env (galaga.window/init draw)]
    (keyboard-listen)
    (game-loop draw (init-env env))))
