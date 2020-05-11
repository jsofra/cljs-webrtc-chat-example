;;; Directory Local Variables
;;; For more information see (info "(emacs) Directory Variables")

((nil . ((cider-clojure-cli-global-options . "-A:dev")
         (cider-default-cljs-repl . custom)
         (cider-custom-cljs-repl-init-form . "(do (user/cljs-repl))")
         (eval . (progn
                   (make-variable-buffer-local 'cider-jack-in-nrepl-middlewares)
                   (add-to-list 'cider-jack-in-nrepl-middlewares "shadow.cljs.devtools.server.nrepl/middleware"))))))
