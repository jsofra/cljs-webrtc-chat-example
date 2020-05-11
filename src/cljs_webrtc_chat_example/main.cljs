(ns cljs-webrtc-chat-example.main
  (:require [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer-macros [<p!]]
            ["firebase/app" :as firebase]
            ["firebase/analytics"]
            ["firebase/firestore"]))

(def firebase-config #js {:apiKey            "AIzaSyBUJTzmqhL0Ut1yR6Th-kqAVz38_4kX1xo"
                          :authDomain        "fir-rtc-b4da8.firebaseapp.com"
                          :databaseURL       "https://fir-rtc-b4da8.firebaseio.com"
                          :projectId         "fir-rtc-b4da8"
                          :storageBucket     "fir-rtc-b4da8.appspot.com"
                          :messagingSenderId "1003664825440"
                          :appId             "1:1003664825440:web:64510b2b76a207f0343705"
                          :measurementId     "G-NLE2WT7NEC"})

(def ice-candidate-config #js {:iceServers           #js [#js {:urls #js ["stun:stun1.l.google.com:19302"
                                                                          "stun:stun2.l.google.com:19302"]}]
                               :iceCandidatePoolSize 10})

(defn ^:dev/after-load start []
  (js/console.log "start"))

(defn ^:dev/before-load stop []
  (js/console.log "stop"))

(defn init-db []
  (firebase/initializeApp firebase-config)
  (firebase/analytics))

(defn write-to-db []
  (let [db    (firebase/firestore)
        users (.collection db "users")]
    (-> users
        (.add #js {:first "Ada"
                   :last "Lovelace"
                   :born 1815})
        (.then (fn [doc-ref]
                 (js/console.log "Document written with ID: " (.-id doc-ref))))
        (.catch (fn [error]
                  (js/console.error "Error adding document: " error))))))

(defn ^:export init []
  (js/console.log "init")
  (init-db)
  (write-to-db))
