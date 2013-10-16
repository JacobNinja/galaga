(ns galaga.core
  (:require [cljs.core.async :as async
             :refer [<! >! chan timeout alts!]]
            [clojure.browser.event :as event]
            [goog.events.KeyHandler :as key-handler]
            [goog.events.KeyCodes :as key-codes]
            [galaga.window])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn init-env [env]
  (let [[height width] (env :dimensions)]
    (assoc env :player 
           [[(-> (/ width 2) .toFixed int) (dec height)]]))) 

(defn game-loop [draw env]
  (go
   (loop [env env]
     (>! draw env)
     (<! (timeout 500))
     (recur env))))

(defn ^:export init []
  (let [draw (chan)
        env (galaga.window/init draw)]
    (game-loop draw (init-env env))))
