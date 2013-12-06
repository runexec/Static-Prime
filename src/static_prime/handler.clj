(ns static-prime.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [hiccup.core :as hc]
            [hiccup.form :as hf]
            [noir.response :as nr]
            [noir.session :as ns]
            [clojure.java.io :as io]))

;; Website Settings

(def index-title "My Website")

(def auth-set 
  {"user1" "password1"
   "admin" "admin"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Edit at your own risk!!


(def admin-path "/sp")
(def auth-path "/auth")
(def save-path "/save")

;; /read/ { html file path }

(def read-url-prefix "/r/")

(def html-path "resources/public/html")
(def template-path "resources/")

(def template-default 
  (str template-path "site.template"))

(def index-page "/index")
(def index-path (str html-path index-page))
(def index-page-url (str read-url-prefix index-page))

(def admin-actions-panel
  (let [edit (format 
              "static_prime.core.adminPanelEdit('%s','%s');"
              read-url-prefix
              admin-path)
        delete (format 
                "static_prime.core.adminPanelDelete('%s','%s');"
                read-url-prefix
                admin-path)]
    (hc/html
     [:div
      [:h3 "Admin Actions"]

      [:button 
       {:id "admin-edit"
        :onclick edit}
       "Edit"]
       "&nbsp;"

      [:button 
       {:id "admin-delete"
        :onclick delete}
       "Delete"]
       "&nbsp"])))

   
(def ^:dynamic *display-admin* true)

;; Website Template Loader

(defn load-template [title body & [template-path]]
  (-> (or template-path
          template-default)
      (slurp :encoding "UTF-8")
      (.replace "{{{title}}}" (hc/h title))
      (.replace "{{{body}}}" body)
      
      ;; Add admin panel

      (.replace "{{{admin}}}"
                (if-not (ns/get :admin) 
                  "" 
                  (if-not *display-admin*
                    ""
                    admin-actions-panel)))))
                    
;; Index Generator 

(defn load-index []
  (let [categories (atom {})
        path->header #(-> (io/file %)
                          .getParent 
                          (.split html-path) 
                          last)

        path->link #(let [link-path (-> % (.split html-path) last)
                          link-path (str read-url-prefix link-path)
                          link-text (-> link-path (.split "/") last hc/h)]
                      [:a {:href link-path} link-text])

        path->header-and-link (fn [p] 
                                [(path->header p) 
                                 (path->link p)])
        links (->> html-path
                   io/file
                   file-seq
                   (filter #(-> % .isDirectory not))
                   (map (memfn getPath))
                   (map path->header-and-link)
                   
                   ;; sort by header
                   
                   (sort-by first)

                   ;; least to greatest

                   reverse)]

    ;; insert links in their proper categories

    (doseq [x links
            :let [[catg links] x]]
      (swap! categories 
             update-in 
             [catg]
             #(into (or (seq %) []) [links])))
    
    ;; category is the header and the links go under the category

    (apply str
           (for [c @categories
                 :let [[c links] c
                       header-text (-> c str (.replace "/" " ") hc/h)
                       links (sort-by #(get-in % [2]) links)]
                 :when c]
             (hc/html 
              [:div 
               [:h3 header-text]
               (map #(into [:p] [%]) links)])))))


;; Static Prime Editor

(defn static-prime []
  (let [csrf (ns/get :csrf)
        csrf (if-not csrf
               (let [u (str (java.util.UUID/randomUUID))]
                 (ns/put! :csrf u)
                 u)
               csrf)]
    (.. (slurp "./static-prime.html" :encoding "UTF-8")
        (replace "{{{csrf}}}" 
                 (hc/html
                  [:input {:type "hidden"
                           :name "csrf"
                           :id "csrf"
                           :value csrf}])))))
    
;; Route Path Finder

(defn route-html-path [file-path]
  (let [fp (clojure.string/replace file-path #"\.\." "")
        fp (if (.. fp (startsWith "/")) fp (str "/" fp))
        fp (str html-path fp)]
    fp))


(defn write-index! []
  (binding [*display-admin* false]
    (spit index-path 
          (load-template index-title 
                         (load-index)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; Routes

(defroutes app-routes

  (GET "/" [] 
       (println "GET /index")
       (slurp index-path :encoding "UTF-8"))
       
  ;; Create new

  (GET admin-path []          
       (if (ns/get :admin)
         (static-prime)
         (nr/redirect auth-path)))

  (GET "/admin" [] (nr/redirect admin-path))

  ;; Edit existing

  (GET [(str admin-path "/:file-path/edit")
        :file-path #".*"]
       [file-path]
       (if-not (ns/get :admin)
         (nr/redirect auth-path)
         (let [file (route-html-path file-path)
               page (static-prime)
               file-path (.. file-path (replace "\"" "\\\""))
               [title & body] (-> file 
                                  (slurp :encoding "UTF-8")
                                  (.split "\n"))
               formats [["<div id=\"preview-page\"></div>"
                         (format
                          "<div id=\"preview-page\">%s</div>"
                          (apply str body))]
                        ["<input name=\"title\" type=\"text\" value=\"\"/>"
                         (format 
                          "<input name=\"title\" type=\"text\" value=\"%s\"/>"
                          title)]
                        ["<input name=\"route\" type=\"text\" value=\"\"/>"
                         (format 
                          "<input name=\"route\" type=\"text\" value=\"%s\"/>"
                          file-path)]]]
           (loop [f formats
                  p page]
             (if-not (seq f)
               p
               (let [[original replace] (first f)]
                 (recur
                  (rest f)
                  (clojure.string/replace 
                   p 
                   (re-pattern original) 
                   replace))))))))


  ;; Delete Existing

  (GET [(str admin-path "/:file-path/delete")
        :file-path #".*"]
       [file-path]
       (if-not (ns/get :admin)
         (nr/redirect auth-path)
         (let [file (route-html-path file-path)
               key (str (java.util.UUID/randomUUID))]

           (ns/put! :delete-path file)
           (ns/put! :delete-key key)

           (hc/html
            (hf/form-to 
             [:post (str admin-path "/delete")]
               [:p "Removing File: " (hc/h file)]
               [:input {:type "hidden"
                        :value key
                        :name "key"}]
               (hf/submit-button "Delete Forever!"))))))

  (POST (str admin-path "/delete")
        {{:keys [key]} :params}
        
        (if-not (ns/get :admin)
          (nr/redirect auth-path)
          (let [k :delete-path
                k2 :delete-key
                path (ns/get k)
                valid-key (ns/get k2)
                error (str "Couldn't delete " path)]

            (ns/remove! k)
            (ns/remove! k2)

            (if-not (= valid-key key)
              "Invalid Delete Token!"
              (do 
                (try
                  (if-not (-> path io/file .delete)
                    (println error)
                    (do (println "Deleted =>" path)
                        (write-index!)))
                  (catch Exception ex
                    (println "admin delete Exception => " ex)))
                (nr/redirect admin-path))))))

  (GET [(str read-url-prefix ":file-path")
        :file-path #".*"] 
       [file-path]

       ;; remove potential directory traversal

       (let [fp (route-html-path file-path)]
         (println "GET" fp)

         ;; first line is the html title and the rest is the body

         (try
           (let [[title & html] (clojure.string/split-lines 
                                 (slurp fp :encoding "UTF-8"))
                 html (apply str html)]
             (load-template title html))
           (catch Exception ex
             (println ex)
             {:status 404
              :headers {}
              :body "Not Found"}))))

  (POST save-path
        {{:keys [title 
                 html
                 route
                 csrf]} :params}        
        
        (if-not (and (ns/get :admin) 
                     (= (ns/get :csrf) csrf))
                    
          "Not Authorized!"
          (let [url (str read-url-prefix route)
                route (-> (route-html-path route)

                          ;; remove potential directory traversal

                          (clojure.string/replace #"\.\." "")
                          io/file 
                          .getAbsolutePath

                          ;; remove any whitespace at the end 

                          clojure.string/trimr
                          io/file)
                dirs (-> route .getParent io/file)]

            ;; dirs must exists before files

            (if-not (.exists dirs)
              (.mkdirs dirs))

            ;; write file

            (spit (.getAbsolutePath route) 
                  (str title "\n\n" html))

            (write-index!)
            (nr/redirect url))))

  (GET auth-path []
       (if (ns/get :admin)
         (nr/redirect admin-path)
         (hc/html
          (hf/form-to 
           [:post auth-path]
           [:p (hf/label "label-admin-user" "Admin User")]
           (hf/text-field "username")
           [:p (hf/label "label-admin-password" "Admin Password")]
           (hf/password-field "password")
           [:p (hf/submit-button "Login")]))))
  
  (POST auth-path
        {{:keys [username
                 password]} :params}

        (if (= password (get auth-set username))
          (do (ns/put! :admin true)
              (nr/redirect admin-path))
          (nr/redirect auth-path)))

  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      handler/site
      ns/wrap-noir-session))
