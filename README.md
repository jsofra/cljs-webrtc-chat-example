# ClojureScript WebRTC Example

This is a small purely ClojureScript application that provides and example of a 1 to 1 peer chat using WebRTC.

The Firebase Firestore is used for signalling between the peers.

The application is hosted on Firebase and deployed using Github actions.

## References

* A lot of inspiration for this was taken from the (Firebase + WebRTC Codelab)[https://webrtc.org/getting-started/firebase-rtc-codelab]
* Much useful information about STUN/TURN servers can be found at (Discovering WebRTC)[https://www.grafikart.fr/tutoriels/webrtc-864]
* A good example of a multiplayer game using WebRTC and Firestore can be found at (Creating a Multiplayer Game with WebRTC)[https://dev.to/rynobax_7/creating-a-multiplayer-game-with-webrtc]
* A free TURN server can be created at (Viagenie)[http://numb.viagenie.ca/]
