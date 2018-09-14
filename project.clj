(defproject com.breezeehr/gybe "0.2.2-SNAPSHOT"
  :description "Gybe is a Hiccup style DOM constructor targetting Apache FOP"
  :url "https://github.com/Breezeemr/gybe/"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [com.breezeehr/hiccup-to-xml-dom "0.1.0"]
                 [org.apache.xmlgraphics/fop "2.0"]]
  :profiles {:test    {:dependencies [[org.apache.pdfbox/pdfbox "1.8.10"]]}
             :repl    {:dependencies [[org.apache.pdfbox/pdfbox "1.8.10"]]}
             ;; lein-test-out does not automatically include the :test profile
             ;; like `lein test` does.
             :jenkins {:dependencies [[org.apache.pdfbox/pdfbox "1.8.10"]]
                       :plugins [[lein-test-out "0.3.1"]]}})
