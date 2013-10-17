(ns galaga.core
  (:require [cljs.core.async :as async
             :refer [<! >! chan timeout alts!]]
            [clojure.browser.event :as event]
            [goog.events.KeyHandler :as key-handler]
            [goog.events.KeyCodes :as key-codes]
            [galaga.window]
            [clojure.set :refer [intersection]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def keyboard (goog.events.KeyHandler. js/document))
(def keyboard-chan (chan 1))
(def valid-directions (set [key-codes/LEFT key-codes/RIGHT]))
(def valid-input (into #{} (cons key-codes/SPACE valid-directions)))

(def direction-cycle (cycle [:left :left :left :left :left nil 
                             :right :right :right :right :right nil]))

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

(defn- generate-enemies [center rows per-row]
  (let [offset (/ per-row 2)]
    (mapcat 
     #(map (fn [column] 
             [(+ center (- (* column 2) offset)) %]) 
           (range per-row))
     (range rows))))

(defn adjust-enemies [env]
  (let [direction (nth direction-cycle (env :enemy-cycle))]
    (merge env {:enemies 
                (map (fn [[x y]]
                       (cond
                        (= direction :left) [(dec x) y]
                        (= direction :right) [(inc x) y]
                        :else [x (inc y)])) (env :enemies))
                :enemy-cycle (inc (env :enemy-cycle))})))

(defn- collision-check [env]
  (let [collisions (intersection (set (env :enemies)) (set (env :projectiles)))]
    (if-not (empty? collisions)
      (assoc env :enemies (remove collisions (env :enemies)))
      env)))

(defn init-env [env]
  (let [[height width] (env :dimensions)
        center (-> (/ width 2) .toFixed int)]
    (merge env {:player [[center (dec height)]]
                :enemies (generate-enemies center 2 4)
                :enemy-cycle 0})))

(defn game-loop [draw env]
  (go
   (>! draw env)
   (loop [env (assoc env :direction (<! keyboard-chan))]
     (let [[keyboard-check _] (alts! [keyboard-chan (timeout 1)])
           next-env (->> env
                         collision-check
                         (fire keyboard-check)
                         (adjust-direction keyboard-check)
                         adjust-player-coords
                         adjust-projectile-coords
                         adjust-enemies)]
       (>! draw next-env)
       (<! (timeout 500))
       (recur next-env)))))

(defn ^:export init []
  (let [draw (chan)
        env (galaga.window/init draw)]
    (keyboard-listen)
    (game-loop draw (init-env env))))
