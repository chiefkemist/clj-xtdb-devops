(ns my-app.layout
  (:require [hiccup.core :as hiccup]
            [my-app.styles :as styles]))

(defn html-response [body]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body (hiccup/html body)})

(defn nav-item [current-page href label]
  [:a {:href href 
       :class (if (= current-page label) 
                styles/nav-item-active
                styles/nav-item)} 
   label])

(defn layout [title & content]
  (html-response
   [:html
    [:head
     [:title (str "XTDB Items - " title)]
     [:meta {:charset "UTF-8"}]
     [:meta {:name "viewport" :content "width=device-width, initial-scale=1.0"}]
     [:script {:src "https://cdn.tailwindcss.com"}]
     [:script {:src "https://unpkg.com/htmx.org@1.9.10"}]
     [:script {:type "text/javascript"}
      "tailwind.config = {
         theme: {
           extend: {
             colors: {
               primary: '#3b82f6',
               danger: '#ef4444'
             }
           }
         }
       }"]
     [:style "
       .htmx-settling { opacity: 0; }
       .htmx-swapping { opacity: 0; }
       [data-loading] { display: none; }
     "]]
    [:body {:class "bg-gray-50 text-gray-900"
            :hx-boost "true"
            :hx-target "#main"}
     [:nav#nav {:class styles/nav-container}
      [:div {:class styles/nav-content}
       [:div {:class "flex items-center justify-between py-3"}
        [:a {:href "/" :class styles/nav-brand} "XTDB Items"]
        [:div {:class styles/nav-list}
         (nav-item title "/" "Home")
         (nav-item title "/items" "Items")]]]]
     [:main#main {:class "min-h-screen"}
      content]
     [:div#loading {:class "fixed top-0 left-0 w-full h-1 bg-blue-600 transition-all"
                    :style "transform: translateX(-100%)"
                    :data-loading ""}]]]))