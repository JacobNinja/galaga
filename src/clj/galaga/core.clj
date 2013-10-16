(ns galaga.core
  (:use [compojure.core]
        [hiccup.core])
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.util.response :refer [header]]
            [clojure.data.json :as json]
            [clojure.string :as string]))

(def page
  (html [:head {:title "Galaga"}
         [:body {:onload "galaga.core.init();"}
          [:div
           [:canvas#world {:width 400 :height 400}]
           [:script {:src "js/dev.js"}]]]]))

(defroutes app-routes
  (GET "/" [] page)
  (route/resources "/")
  (route/not-found "Not found"))

(def app (handler/site app-routes))
