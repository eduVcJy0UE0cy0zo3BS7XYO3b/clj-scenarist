(define-module (g-files packages clojure)
  #:use-module (gnu packages clojure)
  #:use-module (guix packages)
  #:use-module (guix download)
  #:use-module (guix build-system copy)
  #:use-module (gnu packages readline)

  #:use-module ((guix licenses) #:prefix license:)
  #:use-module ((guix gexp) #:select (gexp)))

(define-public clojure-tools-next
  (package
   (name "clojure-tools")
   (version "1.12.0.1517") ;; 10.02.2025
   (source
    (origin
     (method url-fetch)
     (uri (string-append "https://download.clojure.org/install/clojure-tools-" version ".tar.gz"))
     (sha256 (base32 "1vsi7bina5q2rlw0jv92b0f208xpjcwjqzabdz0n0lvsdj3lws9q"))))
   (build-system copy-build-system)
   (arguments
    (list
     #:install-plan
     #~'(("deps.edn" "lib/clojure/")
         ("example-deps.edn" "lib/clojure/")
         ("tools.edn" "lib/clojure/")
         ("exec.jar" "lib/clojure/libexec/")
         (#$(string-append "clojure-tools-" version ".jar") "lib/clojure/libexec/")
         ("clojure" "bin/")
         ("clj" "bin/"))
     #:phases
     #~(modify-phases %standard-phases
                      (add-after 'unpack 'fix-paths
                                 (lambda* (#:key outputs #:allow-other-keys)
                                          (substitute* "clojure"
                                                       (("PREFIX") (string-append #$output "/lib/clojure")))
                                          (substitute* "clj"
                                                       (("BINDIR") (string-append #$output "/bin"))
                                                       (("rlwrap") (which "rlwrap"))))))))
   (inputs (list rlwrap))
   (home-page "https://clojure.org/releases/tools")
   (synopsis "CLI tools for the Clojure programming language")
   (description "The Clojure command line tools can be used to start a
Clojure repl, use Clojure and Java libraries, and start Clojure programs.")
   (license license:epl1.0)))

clojure-tools-next
