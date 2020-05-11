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

(defn register-peer-connection-listeners [peer-connection]
  (doto peer-connection
    (.addEventListener "icegatheringstatechange"
                       (fn [] (js/console.log "ICE gathering state changed: " (.-iceGatheringState peer-connection))))
    (.addEventListener "connectionstatechange"
                       (fn [] (js/console.log "Connection state change: " (.-connectionState peer-connection))))
    (.addEventListener "signalingstatechange"
                       (fn [] (js/console.log "Signaling state changed: " (.-signalingState peer-connection))))
    (.addEventListener "iceconnectionstatechange"
                       (fn [] (js/console.log "ICE connection state changed: " (.-iceConnectionState peer-connection))))))

(defn collect-ice-candidates [room-ref peer-connection]
  (let [caller-candidates (.collection room-ref "callerCandidates")]
    (.addEventListener peer-connection "icecandidate"
                       (fn [event]
                         (let [candidate (.-candidate event)]
                           (if candidate
                             (do
                               (js/console.log "Got candidate: " candidate)
                               (.add caller-candidates (.toJSON candidate)))
                             (js/console.log "Got final candidate!"))))))
  (-> room-ref
      (.collection "calleeCandidates")
      (.onSnapshot (fn [snapshot]
                     (doseq [change (.docChanges snapshot)]
                       (when (= (.-type change) "added")
                         (.addIceCandidate (js/RTCIceCandidate. (.data (.-doc change))))))))))

(defn create-room [db peer-connection]
  (go
    (let [room-ref (-> db (.collection "rooms") (.doc))
          offer    (<p! (.createOffer peer-connection))]
      (collect-ice-candidates room-ref peer-connection)
      (<p! (.setLocalDescription peer-connection offer))
      (js/console.log "Created offer:" offer)
      (<p! (.set room-ref (clj->js {:offer {:type (.-type offer)
                                            :sdp  (.-sdp offer)}})))
      (js/console.log "New room created with SDP offer. Room ID: " (.-id room-ref)))))

(defn ^:export init []
  (js/console.log "init")
  (init-db)
  (write-to-db)

  (let [db              (firebase/firestore)
        peer-connection (js/RTCPeerConnection. ice-candidate-config)]
    (register-peer-connection-listeners peer-connection)

    (create-room db peer-connection)
    ))
