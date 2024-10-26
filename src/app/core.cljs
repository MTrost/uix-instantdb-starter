(ns app.core
  (:require
    [cljs.spec.alpha :as s]
    ["@instantdb/react" :as instantdb]
    [uix.core :as uix :refer [defui $]]
    [uix.dom]))

(def init-db instantdb/init)
(def tx instantdb/tx)
(def id instantdb/id)

(def app-id "your-instant-db-id")

(def db (delay (init-db #js {:appId app-id})))

(defui header []
  ($ :header.app-header
    ($ :img {:src "https://raw.githubusercontent.com/pitch-io/uix/master/logo.png"
             :width 32})))

(defui footer []
  ($ :footer.app-footer
    ($ :small "made with "
              ($ :a {:href "https://github.com/pitch-io/uix"}
                    "UIx"))))

(defui text-field [{:keys [on-add-todo]}]
  (let [[value set-value!] (uix/use-state "")]
    ($ :input.text-input
      {:value value
       :placeholder "Add a new todo and hit Enter to save"
       :on-change (fn [^js e]
                    (set-value! (.. e -target -value)))
       :on-key-down (fn [^js e]
                      (when (= "Enter" (.-key e))
                        (set-value! "")
                        (on-add-todo {:text value
                                      :status "unresolved"})))})))

(defui editable-text [{:keys [text text-style on-done-editing]}]
  (let [[editing? set-editing!] (uix/use-state false)
        [editing-value set-editing-value!] (uix/use-state "")]
    (if editing?
      ($ :input.todo-item-text-field
        {:value editing-value
         :auto-focus true
         :on-change (fn [^js e]
                      (set-editing-value! (.. e -target -value)))
         :on-key-down (fn [^js e]
                        (when (= "Enter" (.-key e))
                          (set-editing-value! "")
                          (set-editing! false)
                          (on-done-editing editing-value)))})
      ($ :span.todo-item-text
        {:style text-style
         :on-click (fn [_]
                     (set-editing! true)
                     (set-editing-value! text))}
        text))))

(s/def :todo/text string?)
(s/def :todo/status #{"resolved" "unresolved"})

(s/def :todo/item
  (s/keys :req-un [:todo/text :todo/status]))

(defui todo-item
  [{:keys [id text status on-update-todo on-delete-todo] :as props}]
  {:pre [(s/valid? :todo/item props)]}
  ($ :.todo-item
    {:key id}
    ($ :input.todo-item-control
      {:type :checkbox
       :checked (= status "resolved")
       :on-change (fn [_]
                    (on-update-todo (update props :status {"unresolved" "resolved"
                                                          "resolved" "unresolved"})
                                    id))})
    ($ editable-text
      {:text text
       :text-style {:text-decoration (when (= :resolved status) :line-through)}
       :on-done-editing (fn [value]
                          (on-update-todo (assoc props :text value) id))})
    ($ :button.todo-item-delete-button
      {:on-click (fn [_]
                   (on-delete-todo id))}
      "×")))

(defn update-todo!
  ([todo] (update-todo! todo nil))
  ([todo instant-id]
   (.transact @db
              (.update (aget (.-todos tx) (or instant-id (id)))
                       (clj->js (select-keys todo [:id :text :status]))))))

(defn delete-todo!
  [instant-id]
  (.transact @db
             (.delete (aget (.-todos tx) instant-id))))

(defui app []
  (let [{:keys [data]} (js->clj (.useQuery @db (clj->js {:todos {}}))
                                :keywordize-keys true)]
    ($ :.app
      ($ header)
      ($ text-field {:on-add-todo update-todo!})
      (for [todo (:todos data)]
        ($ todo-item
          (assoc todo :key (:id todo)
                      :on-update-todo update-todo!
                      :on-delete-todo delete-todo!)))
      ($ footer))))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defn render []
  (uix.dom/render-root ($ app) root))

(defn ^:export init []
  (render))
