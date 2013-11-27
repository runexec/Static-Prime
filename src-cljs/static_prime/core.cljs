(ns static-prime.core
  (:require 
   [domina :as da]
   [domina.css :as dc]
   [domina.events :as de]
   [markdown.core :as md]
   [crate.core :as cc]
   [cljs.core.async :refer [timeout chan <! >! put!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))


;; refactor, make video, and release alpha

(def sections 
  "key is the uniq id and the value is {:html x raw x}"
  (atom []))

(def update-id
  "If set, the add/apply button will apply changes to id"
  (atom false))

(def section-c (chan))

;; Static Prime Editor Input Group / Text Area

(def input-group-id "#input-group")
(def input-area-id "#input-area")

;; Static Prime Editor Preview Area / Page

(def preview-area-id "#preview-area")
(def preview-page-id "#preview-page")

;; Static Prime Editor Add / Cancel Buttons

(def add-section-button-id "#add-section")
(def cancel-section-button-id "#cancel-section")

;; Static Prime Editor Raw / HTML Buttons

(def export-html-button-id "#button-export-html")
(def export-raw-button-id "#button-export-raw")

;; Static Prime Editor Save Form / Hidden HTML Input

(def save-form-id "#save-form")
(def save-html-id "#save-html")

;; Produces Section ID

(defn unique-id [] (str (.getTime (js/Date.))))

;; Values of Input Content / Preview 

(defn input-content []
  (da/value
   (dc/sel input-area-id)))

(defn preview-html []
  (da/html
   (dc/sel preview-area-id)))

(defn sections->type 
  "t is :raw or :html
  This function is responsible for converting the entire
  preview page to the final page."
  [t]
  (let [t (keyword t)
        -vals (juxt :id t)
        pred #(let [[id value] (-vals %)]
                (if (= t :raw)
                  (str value "\n")
                  (str "<div class=\"spsection\" id=\""
                       id
                       "\">"
                       value
                       "</div>")))]
    (reduce 
     str
     (map pred @sections))))
    
(defn get-section 
  "Returns a page preview section by id"
  [id]
  (first
   (drop-while #(not= (:id %) id) @sections)))

(defn remove-section! 
  "Removes a page preview section by id"
  [id]
  (swap! sections
         (fn [x]
           (filter #(not= (:id %) id) x))))

(defn hover-only-edit-pane! []
  (da/remove-class!
   (dc/sel input-group-id)
   "edit-hover"))

(defn clear-input-and-preview! []
  (da/set-value! (dc/sel input-area-id) "")
  (da/set-html! (dc/sel preview-area-id) ""))

(defn update-view! 
  "Updates input preview area"
  [evt]    
  (go 
   (let [content (input-content)
         wmd (clojure.string/split content #"<md>")]
     (da/set-html!
      (dc/sel preview-area-id)
      (reduce
       str
       (map #(if-not (re-find #"</md>" %)
               %
               (let [[m other] (clojure.string/split % #"</md>")]
                 (str (md/mdToHtml m) other)))
            wmd))))))
  
(de/listen! 
 (dc/sel input-area-id)
 :keyup
 update-view!)

(defn cancel! 
  "Cancel editing action and optional editor viewing"
  [evt]
  (hover-only-edit-pane!)
  (reset! update-id false)
  (clear-input-and-preview!))

(de/listen!
 (dc/sel cancel-section-button-id)
 :click
 cancel!)

(declare 
 load-page-preview!)

;; On mouse over save form => Set hidden HTML input value.

(de/listen!
 (dc/sel save-form-id)
 :mouseover
 (fn [evt]
   (load-page-preview!)
   (da/set-value!
    (dc/sel save-html-id)
    (sections->type :html))))

;; Button Click Export Page => HTML

(defn page->html! [evt]
  ;; refresh sections
  (load-page-preview!)
  (da/set-value!
   (dc/sel input-area-id)
   (sections->type :html)))

(de/listen!
 (dc/sel export-html-button-id)
 :click
 page->html!)

;; Button Click Export Page => RAW

(defn page->raw! [evt]
  ;; refresh sections
  (load-page-preview!)
  (da/set-value!
   (dc/sel input-area-id)
   (sections->type :raw)))

(de/listen!
 (dc/sel export-raw-button-id)
 :click
 page->raw!)

;; Page Edit Section Remove Button Action

(defn remove-button-listener! [id]
  (de/listen! 
   (dc/sel (str "#remove-" id))
   :click
   (fn [evt]
     (swap! sections 
            (fn [x] 
              (filter #(-> % :id (not= id)) x)))
     (load-page-preview!))))

;; Page Edit Section Edit Button Action

(defn edit-button-listener! [id raw]
  (de/listen! 
   (dc/sel (str "#edit-" id))
   :click
   (fn [evt]
     (reset! update-id id)

     (da/set-value!
      (dc/sel input-area-id)
      raw)

     (update-view! true)

     ;; Force Editor Drop Down

     (da/add-class!
      (dc/sel input-group-id)
      "edit-hover")

     ;; Remove Force 

     (go 
      (<! (timeout 100))
      (da/remove-class!
       (dc/sel input-group-id)
       "edit-hover")))))

;; Page Edit Section Swap Button Action

(let [swap-c (chan)]

  (defn swap-button-listener! [id]
    (de/listen! 
     (dc/sel (str "#swap-" id))
     :click
     (fn [evt]
       (put! swap-c id))))

  ;; Async Swap Loop

  (go 
   (while true
     (let [this-id (<! swap-c)
           that-id (<! swap-c)
           this (assoc (get-section this-id) :id that-id)
           that (assoc (get-section that-id) :id this-id)]
       (remove-section! this-id)
       (remove-section! that-id)
       (swap! sections conj this that)
       (load-page-preview!)))))

;; Page Edit Section Apply Button Action

(defn add-section! [evt]
  (go
   (let [section-id (unique-id)
         raw (input-content)
         html (preview-html)
         uid @update-id]

     ;; Add New Section

     (case (string? uid)
       false (swap! sections
                    conj
                    {:id section-id
                     :raw raw
                     :html html})

       ;; Edit Current Section

       true (do 
              (reset! sections
                      (conj
                       (filter #(not= (:id %) uid) @sections)
                       {:id uid
                        :raw raw
                        :html html}))
              (reset! update-id false)))

     (cancel! true)
     (load-page-preview!))))
            
(de/listen! 
 (dc/sel add-section-button-id)
 :click
 add-section!)
  
(defn load-page-preview! 
  "Loads entire page preview"
  []
  (go 
   (swap! sections #(sort-by :id %))
   (da/set-html! (dc/sel preview-page-id) "")

   (doseq [s @sections
           :let [{:keys [id raw html]} s]]

     (da/append!
      (dc/sel preview-page-id)
      (cc/html
       [:div {:class 'spsection 
              :id id}
        [:raw
         [:button 
          {:id (str "remove-" id)}
          "Remove"]
         [:button 
          {:id (str "swap-" id)}
          "Swap"]
         [:button 
          {:id (str "edit-" id)}
          "Edit"]]
        [:br]
        (cc/raw html)]))
     
     (remove-button-listener! id)
     (edit-button-listener! id raw)
     (swap-button-listener! id))))

(defn ^:export loadEdits 
  "Used to load sections of existing pages"
  []
  (let [elements (.. js/document 
                     (getElementsByClassName "spsection"))
        element-ids (map #(.-id %) elements)]
    (go 
     (doseq [id element-ids
             :let [gid (str "#" id)
                   html (da/html 
                         (dc/sel gid))
                   raw html
                   html-menu (cc/html 
                               [:raw
                                [:button 
                                 {:id (str "remove-" id)}
                                 "Remove"]
                                [:button 
                                 {:id (str "swap-" id)}
                                 "Swap"]
                                [:button 
                                 {:id (str "edit-" id)}
                                 "Edit"]]
                               [:br])]]
       (swap! sections
              conj 
              {:id id :html raw :raw raw})
       
       (da/set-html! (dc/sel gid) html-menu)
       (da/append! (dc/sel gid) raw)               
       (remove-button-listener! id)
       (edit-button-listener! id raw)
       (swap-button-listener! id)))
     (load-page-preview!)))

(defn page-to-admin-redirect!
  "admin-type can be :edit or :delete"
  [read-path 
   admin-path
   admin-type]
  (let [admin-path (str admin-path "/")
        path (->> 
              (-> js/document
                  .-location
                  str
                  (.split "/"))
              (drop 3)
              (interpose "/")
              (reduce str)
              (str "/"))
        path (-> path
                 (.replace read-path 
                           admin-path)
                 (str "/" (name admin-type)))]
    (set! (.-location js/document) path)))
                     
(defn ^:export adminPanelEdit 
  [read-path admin-path]
  (page-to-admin-redirect! read-path admin-path :edit))

(defn ^:export adminPanelDelete 
  [read-path admin-path]
  (page-to-admin-redirect! read-path admin-path :delete))
