(ns cljs-webrtc-chat-example.main
  (:require [cljs.tools.reader.edn :as edn]
            [cljs.core.async :refer [go]]
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

(def ice-candidate-config (clj->js {:iceServers            [{:urls ["stun:stun1.l.google.com:19302"
                                                                    "stun:stun2.l.google.com:19302"]}]
                                    :iceCandidatePoolSize 10}))

(defn ^:dev/after-load start []
  (js/console.log "start"))

(defn ^:dev/before-load stop []
  (js/console.log "stop"))

(defn init-db []
  (firebase/initializeApp firebase-config)
  (firebase/analytics))

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

(defn collect-ice-candidates [room-ref peer-connection local-name remote-name]
  (let [candidates (.collection room-ref local-name)]
    (.addEventListener peer-connection "icecandidate"
                       (fn [event]
                         (js/console.log "icecandidate" event)
                         (if-let [candidate (.-candidate event)]
                           (do
                             (js/console.log "Got candidate: " candidate)
                             (.add candidates (.toJSON candidate)))
                           (js/console.log "Got final candidate!")))))
  (-> room-ref
      (.collection remote-name)
      (.onSnapshot (fn [snapshot]
                     (doseq [change (.docChanges snapshot)]
                       (go (when (= (.-type change) "added")
                             (let [data (.data (.-doc change))]
                               (js/console.log (str "Got new remote ICE candidate: " (js/JSON.stringify data)))
                               (<p! (.addIceCandidate peer-connection
                                                      (js/RTCIceCandidate. (.data (.-doc change)))))))))))))

(defn listen-for-answer [room-ref peer-connection]
  (.onSnapshot room-ref
               (fn [snapshot]
                 (js/console.log "New Snapshot" snapshot)
                 (go
                   (let [data   (.data snapshot)
                         answer (goog.object/get data "answer")]
                     (when (and (not (goog.object/get peer-connection "currentRemoteDescription")) data answer)
                       (js/console.log "Set remote description: " answer)
                       (<p! (.setRemoteDescription peer-connection (js/RTCSessionDescription. answer)))))))))

(defn create-offer [db peer-connection]
  (go
    (let [offer    (<p! (.createOffer peer-connection))
          room-ref (<p! (.add (.collection db "rooms") (clj->js {:offer {:type (.-type offer)
                                                                         :sdp  (.-sdp offer)}})))]
      (<p! (.setLocalDescription peer-connection offer))
      {:offer offer :room-ref room-ref})))

(defn create-room [db peer-connection data-channel]
  (reset! data-channel (.createDataChannel peer-connection "senderChannel"))
  (go
    (let [{:keys [offer room-ref]} (<! (create-offer db peer-connection))]
      (js/console.log "Offer created: " offer)
      (js/console.log "New room created with SDP offer. Room ID: " (.-id room-ref))
      (doto room-ref
        (collect-ice-candidates peer-connection "callerCandidates" "calleeCandidates")
        (listen-for-answer peer-connection)))))

(defn create-answer [room-ref room-snapshot peer-connection]
  (go
    (let [snapshot-data (.data room-snapshot)
          offer         (goog.object/get snapshot-data "offer")]
      (js/console.log "Got an offer: " offer)
      (<p! (.setRemoteDescription peer-connection offer))
      (let [answer (<p! (.createAnswer peer-connection))]
        (js/console.log "Created answer: " answer)
        (<p! (.setLocalDescription peer-connection answer))
        (<p! (.update room-ref (clj->js {:answer {:type (.-type answer)
                                                  :sdp  (.-sdp answer)}})))))))

(defn join-room [db peer-connection room-id data-channel]
  (go
    (let [room-ref      (.doc (.collection db "rooms") (str room-id))
          room-snapshot (<p! (.get room-ref))]
      (js/console.log "Got a room:" (.-exists room-snapshot))

      (.addEventListener peer-connection "datachannel"
                         (fn [event]
                           (js/console.log "Got receiving datachannel:" (.-channel event))
                           (reset! data-channel (.-channel event))))

      (when (.-exists room-snapshot)
        (doto room-ref
          (create-answer room-snapshot peer-connection)
          (collect-ice-candidates peer-connection "calleeCandidates" "callerCandidates"))))))

(defn write-message [{:keys [author message]}]
  (let [chat-text-area (.querySelector js/document "#chat-text-area")]
    (set! (.-value chat-text-area)
          (str (.-value chat-text-area) "\n" author ": " message))))

(defn add-room-options [rooms]
  (let [room-select (.querySelector js/document "#join-room-select")]
    (doseq [room rooms]
      (let [room-id (goog.object/get room "id")]
        (.add room-select
              (doto (.createElement js/document "option")
                (goog.object/set "text" room-id)
                (goog.object/set "value" room-id)))))))


(defn ^:export init []
  (js/console.log "init")
  (init-db)

  (let [db              (firebase/firestore)
        peer-connection (js/RTCPeerConnection. ice-candidate-config)
        data-channel    (atom nil)]
    (register-peer-connection-listeners peer-connection)

    (let [create-btn (.querySelector js/document "#create-room-btn")]
      (.addEventListener create-btn "click"
                         #(go (let [room-ref (<! (create-room db peer-connection data-channel))
                                    alert    (.querySelector js/document "#create-room-alert")]
                                (set! (.-textContent alert) (str "Room created with id: " (.-id room-ref)))
                                (set! (.-hidden alert) false)
                                (set! (.-disabled create-btn) true)))))

    (-> js/document
        (.querySelector "#join-room-btn")
        (.addEventListener "click"
                           #(go (let [room-id (.-value (.querySelector js/document "#join-room-select"))
                                      alert   (.querySelector js/document "#join-room-alert")]
                                  (<! (join-room db peer-connection room-id data-channel))
                                  (set! (.-textContent alert) (str "Room joined with id: " room-id))
                                  (set! (.-hidden alert) false)))
                           #js {:once true}))

    (let [rooms-ref (.collection db "rooms")]
      (go
        (add-room-options (.-docs (<p! (.get rooms-ref)))))
      (.onSnapshot rooms-ref
                   (fn [rooms-snapshot]
                     (add-room-options (.-docs rooms-snapshot)))))

    (-> js/document
        (.querySelector "#send-btn")
        (.addEventListener "click"
                           (fn [_]
                             (when @data-channel
                               (let [message {:author  (.-value (.querySelector js/document "#user-name-input"))
                                              :message (.-value (.querySelector js/document "#message-input"))}]
                                 (write-message message)
                                 (.send @data-channel (str message)))))))

    (add-watch data-channel :receiver
               (fn [_ _ _ new-channel]
                 (when new-channel
                   (.addEventListener new-channel "message"
                                      (fn [event]
                                        (let [chat-text-area (.querySelector js/document "#chat-text-area")]
                                          (set! (.-value chat-text-area)
                                                (write-message (edn/read-string (.-data event))))))))))))
