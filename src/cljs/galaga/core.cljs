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
(def valid-directions {key-codes/LEFT :left
                       key-codes/RIGHT :right})
(def fire-key key-codes/SPACE)
(def valid-input (assoc valid-directions fire-key fire-key))

(defn circle [radius]
  (mapcat #(repeatedly radius (partial identity %)) 
          [:down-left :down-right :up-right :up-left]))

(def game-width 10)
(def direction-cycle (cycle (mapcat 
                      #(conj (vec (repeatedly game-width (partial identity %))) :down) 
                      [:left :right])))

(defn- adjust-coords-by-direction [direction [x y]]
  (if direction 
    (condp = direction
      :left [(dec x) y]
      :right [(inc x) y]
      :up [x (dec y)]
      :down [x (inc y)]
      :up-left [(dec x) (dec y)]
      :up-right [(inc x) (dec y)]
      :down-left [(dec x) (inc y)]
      :down-right [(inc x) (inc y)])
    [x y]))

(defrecord Enemy [coords movements]
  Object
  (tick [this]
    (let [next-coords (adjust-coords-by-direction (first movements) coords)]
      (merge this {:coords next-coords
                   :movements (lazy-seq (rest movements))}))))

(defn- keyboard-listen []
  (event/listen keyboard "key" 
                (fn [k]
                  (let [key-code (.-keyCode k)]
                    (when (valid-input key-code)
                      (go (>! keyboard-chan key-code)))))))

(defn- adjust-direction [direction env]
  (assoc env :direction (valid-directions direction)))

(defn- adjust-player-coords [env]
  (assoc env :player (adjust-coords-by-direction (env :direction) (env :player))))
  
(defn- fire [keyboard-input env]
  (if (= keyboard-input fire-key)
    (assoc env :projectiles (cons (env :player) (env :projectiles)))
    env))

(defn- adjust-projectile-coords [env]
  (assoc env :projectiles 
         (filter (fn [[x y]] (>= y 0)) 
                 (map (fn [[x y]] [x (dec y)]) (env :projectiles)))))

(defn- generate-enemy-coords [center rows per-row]
  (let [offset (dec (/ (* rows per-row) 2))]
    (mapcat 
     #(map (fn [column] 
             [(+ center (- (* column 2) offset)) %]) 
           (range per-row))
     (range rows))))

(defn adjust-enemies [env]
 (assoc env :enemies (map #(-> % .tick) (env :enemies))))

(defn- collision-check [env]
 (let [collisions (intersection (set (map :coords (env :enemies))) (set (env :projectiles)))]
   (if-not (empty? collisions)
     (assoc env :enemies (remove #(collisions (:coords %)) (env :enemies)))
     env)))

(defn init-env [env]
 (let [[height width] (env :dimensions)
       center (-> (/ width 2) .toFixed int)
       offset (/ game-width 2)]
   (merge env {:player [center (dec height)]
               :enemies (map 
                         #(->Enemy % (concat (circle (/ height 4)) 
                                             (drop offset direction-cycle)))
                         (generate-enemy-coords center 2 4))})))

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
       (<! (timeout 200))
       (recur next-env)))))

(defn ^:export init []
  (let [draw (chan)
        env (galaga.window/init draw)]
    (keyboard-listen)
    (game-loop draw (init-env env))))
