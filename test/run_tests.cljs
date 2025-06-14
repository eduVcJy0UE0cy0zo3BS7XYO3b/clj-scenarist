(ns run-tests
  (:require [cljs.test :refer-macros [run-tests]]
            [scenarist.db-test]
            [scenarist.typewriter-test]
            [scenarist.script-test]
            [scenarist.integration-test]
            [scenarist.ui-test]
            [scenarist.e2e-test]
            [scenarist.loader-test]))

(defn -main []
  (run-tests 'scenarist.db-test
             'scenarist.typewriter-test
             'scenarist.script-test
             'scenarist.integration-test
             'scenarist.ui-test
             'scenarist.e2e-test
             'scenarist.loader-test))

(set! *main-cli-fn* -main)
