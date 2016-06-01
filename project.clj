(defproject com.breezeehr/gybe "0.2.1-SNAPSHOT"
  :description "Gybe is a Hiccup style DOM constructor targetting Apache FOP"
  :url "https://github.com/Breezeemr/gybe/"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.apache.pdfbox/pdfbox "1.8.10"] ;; for testing
                 [org.apache.xmlgraphics/fop "2.0"]]
  :profiles {:jenkins {:plugins [[lein-test-out "0.3.1"]]}})
