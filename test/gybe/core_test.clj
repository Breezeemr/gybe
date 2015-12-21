(ns gybe.core-test
  (:require [clojure.test :refer :all]
            [gybe.core :refer :all]
            [clojure.data :refer [diff]]
            [clojure.xml :refer [parse]])
  (:import [java.io File]
           [org.apache.pdfbox.util PDFTextStripper]
           [org.apache.pdfbox.pdmodel PDDocument]))

(deftest dom-test
  (testing "can create a single xml dom node with no attributes & no content"
    (is (= (serialize-dom (->dom [:fo:box]))
           "<fo:box xmlns:fo=\"http://www.w3.org/1999/XSL/Format\"/>")))

  (testing "can create a single xml dom node with no attributes"
    (is (= (serialize-dom (->dom [:fo:box "garland texas"]))
           "<fo:box xmlns:fo=\"http://www.w3.org/1999/XSL/Format\">garland texas</fo:box>")))

  (testing "can create a single xml dom node"
    (is (= (serialize-dom (->dom [:fo:box {:keep-together.within-page "always"} "garland texas"]))
           "<fo:box xmlns:fo=\"http://www.w3.org/1999/XSL/Format\" keep-together.within-page=\"always\">garland texas</fo:box>")))

  (testing "can create nested dom node with no attributes"
    (is (= (serialize-dom (->dom [:fo:root [:fo:layout-master-set "f"]]))
           "<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\"><fo:layout-master-set>f</fo:layout-master-set></fo:root>")))

  (testing "can create nested dom node with attributes on child"
    (is (= (serialize-dom
            (->dom [:fo:root [:fo:layout-master-set {:d "dork"} "f"]]))
           "<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\"><fo:layout-master-set d=\"dork\">f</fo:layout-master-set></fo:root>")))

  (testing "can create nested dom node with attributes on parent"
    (is (= (serialize-dom
            (->dom [:fo:root {:d "dork"} [:fo:layout-master-set "f"]]))
           "<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\" d=\"dork\"><fo:layout-master-set>f</fo:layout-master-set></fo:root>"))))

(deftest fop-test
  (testing "can create a basic FOP document"
    (is (= (serialize-dom
            (->fop
             [:fo:box {:keep-together.within-page "always"} "garland texas"]))
           "<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\"><fo:layout-master-set><fo:simple-page-master margin-bottom=\"1in\" margin-left=\"1in\" margin-right=\"1in\" margin-top=\"1in\" master-name=\"letter\" page-height=\"11in\" page-width=\"8.5in\"><fo:region-body/></fo:simple-page-master></fo:layout-master-set><fo:page-sequence master-reference=\"letter\"><fo:flow flow-name=\"xsl-region-body\"><fo:box keep-together.within-page=\"always\">garland texas</fo:box></fo:flow></fo:page-sequence></fo:root>")))

  (testing "can create a basic FOP document with multiple elements in <fo:flow/>"
    (is (= (serialize-dom
            (->fop
             [:fo:block {:keep-together.within-page "always"} "garland texas"]
             [:fo:block {:keep-together.within-page "always"} "russia texas"]))
           "<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\"><fo:layout-master-set><fo:simple-page-master margin-bottom=\"1in\" margin-left=\"1in\" margin-right=\"1in\" margin-top=\"1in\" master-name=\"letter\" page-height=\"11in\" page-width=\"8.5in\"><fo:region-body/></fo:simple-page-master></fo:layout-master-set><fo:page-sequence master-reference=\"letter\"><fo:flow flow-name=\"xsl-region-body\"><fo:block keep-together.within-page=\"always\">garland texas</fo:block><fo:block keep-together.within-page=\"always\">russia texas</fo:block></fo:flow></fo:page-sequence></fo:root>")))

  
  (testing "more complex FOP document; this fails because the text fields must exactly match the FO document.  ie. (not= \"Page\" \"\n\t\tPage\n        \")"
    (let [fop-doc (->dom
                   [:fo:root
                    [:fo:layout-master-set
                     [:fo:simple-page-master
                      {:page-height "29.7cm", :page-width "21cm",
                       :margin-top "1cm", :margin-bottom "2cm",
                       :margin-left "1.5cm", :margin-right "1.5cm", :master-name "first"}
                      [:fo:region-body {:margin-top "1cm"}]
                      [:fo:region-before {:extent "1cm"}]
                      [:fo:region-after {:extent "1.5cm"}]]]
                    [:fo:page-sequence {:master-reference "first"}
                     [:fo:static-content {:flow-name "xsl-region-before"}
                      [:fo:block {:text-align "end", :font-size "10pt", :line-height "14pt"}
                       "table examples"]]
                     [:fo:static-content {:flow-name "xsl-region-after"}
                      [:fo:block {:text-align "end", :font-size "10pt", :line-height "14pt"}
                       "Page "
                       [:fo:page-number]]]
                     [:fo:flow {:flow-name "xsl-region-body"}
                      [:fo:block {:space-after.optimum "15pt", :space-before.optimum "3pt"}
                       "Table 1: cell border"]
                      [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "separate"}
                       [:fo:table-column {:column-width "3cm"}]
                       [:fo:table-column {:column-width "3cm"}]
                       [:fo:table-column {:column-width "3cm"}]
                       [:fo:table-column {:column-width "3cm"}]
                       [:fo:table-column {:column-width "3cm"}]
                       [:fo:table-column {:column-width "2cm"}]
                       [:fo:table-body
                        [:fo:table-row
                         [:fo:table-cell {:border-left-style "solid", :border-left-width "0.5pt", :border-left-color "green"}
                          [:fo:block {:text-align "center"}
                           "green left"]]
                         [:fo:table-cell {:border-top-style "solid", :border-top-width "0.5pt", :border-top-color "red"}
                          [:fo:block {:text-align "center"}
                           "red top"]]
                         [:fo:table-cell {:border-right-style "solid", :border-right-width "0.5pt", :border-right-color "blue"}
                          [:fo:block {:text-align "center"}
                           "blue right"]]
                         [:fo:table-cell {:border-bottom-style "solid", :border-bottom-width "0.5pt", :border-bottom-color "yellow"}
                          [:fo:block {:text-align "center"}
                           "yellow bottom"]]
                         [:fo:table-cell {:border-top-style "solid", :border-bottom-style "solid", :border-left-width "0.5pt", :border-right-style "solid", :border-left-color "green", :border-right-width "0.5pt", :border-bottom-width "0.5pt", :border-right-color "blue", :border-left-style "solid", :border-top-width "0.5pt", :border-top-color "red", :border-bottom-color "yellow"}
                          [:fo:block {:text-align "center"}
                           "all"]]
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "text for an extra line in the table row"]]]
                        [:fo:table-row [:fo:table-cell {:border-style "solid", :border-left-width "2pt", :border-color "green"}
                                        [:fo:block {:text-align "center"}
                                         "2pt"]]
                         [:fo:table-cell {:border-top-style "dashed", :border-top-width "2pt", :border-top-color "red"}
                          [:fo:block {:text-align "center"}
                           "2pt"]]
                         [:fo:table-cell {:border-right-style "dotted", :border-right-width "2pt", :border-right-color "blue"}
                          [:fo:block {:text-align "center"}
                           "2pt"]]
                         [:fo:table-cell {:border-bottom-style "double", :border-bottom-width "2pt", :border-bottom-color "yellow"}
                          [:fo:block {:text-align "center"}
                           "2pt"]]
                         [:fo:table-cell {:border-top-style "dashed", :border-bottom-style "dotted", :border-left-width "2pt", :border-right-style "double", :border-left-color "green", :border-right-width "2pt", :border-bottom-width "2pt", :border-right-color "blue", :border-left-style "solid", :border-top-width "2pt", :border-top-color "red", :border-bottom-color "yellow"}
                          [:fo:block {:text-align "center"}
                           "2pt"]]
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "text for an extra line in the table row"]]]
                        [:fo:table-row [:fo:table-cell {:border-left-style "solid", :border-left-width "10pt", :border-left-color "green"}
                                        [:fo:block {:text-align "center"}
                                         "10pt"]]
                         [:fo:table-cell {:border-top-style "solid", :border-top-width "10pt", :border-top-color "red"}
                          [:fo:block {:text-align "center"}
                           "10pt"]]
                         [:fo:table-cell {:border-right-style "solid", :border-right-width "10pt", :border-right-color "blue"}
                          [:fo:block {:text-align "center"}
                           "10pt"]]
                         [:fo:table-cell {:border-bottom-style "solid", :border-bottom-width "10pt", :border-bottom-color "yellow"}
                          [:fo:block {:text-align "center"}
                           "10pt"]]
                         [:fo:table-cell {:border-top-style "solid", :border-bottom-style "solid", :border-left-width "2pt", :border-right-style "solid", :border-left-color "green", :border-right-width "8pt", :border-bottom-width "10pt", :border-right-color "blue", :border-left-style "solid", :border-top-width "4pt", :border-top-color "red", :border-bottom-color "yellow"}
                          [:fo:block {:text-align "center"}
                           "2pt - 10pt"]]
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "text for an extra line in the table row"]]]
                        [:fo:table-row [:fo:table-cell {:border-style "solid", :border-width "0.5pt", :border-color "green"}
                                        [:fo:block {:text-align "center"}
                                         "0.5pt"]]
                         [:fo:table-cell {:border-style "solid", :border-width "1pt", :border-color "red"}
                          [:fo:block {:text-align "center"}
                           "1pt"]]
                         [:fo:table-cell {:border-style "solid", :border-width "2pt", :border-color "blue"}
                          [:fo:block {:text-align "center"}
                           "2pt"]]
                         [:fo:table-cell {:border-style "solid", :border-width "10pt", :border-color "yellow"}
                          [:fo:block {:text-align "center"}
                           "10pt"]]
                         [:fo:table-cell {:border-style "solid", :border-width "20pt", :border-color "yellow"}
                          [:fo:block {:text-align "center"}
                           "20pt"]]
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "text for an extra line in the table row"]]]]]
                      [:fo:block {:space-after.optimum "15pt", :space-before.optimum "30pt"}
                       "Table 2: row borders"]
                      [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "collapse"}
                       [:fo:table-column {:column-width "3cm"}]
                       [:fo:table-column {:column-width "3cm"}]
                       [:fo:table-column {:column-width "3cm"}]
                       [:fo:table-column {:column-width "3cm"}]
                       [:fo:table-body [:fo:table-row {:border-left-style "solid", :border-left-width "0.5pt", :border-left-color "green"}
                                        [:fo:table-cell [:fo:block {:text-align "center"}
                                                         "row with"]]
                                        [:fo:table-cell [:fo:block {:text-align "center"}
                                                         "green left"]]
                                        [:fo:table-cell [:fo:block {:text-align "center"}
                                                         "border"]]
                                        [:fo:table-cell [:fo:block {:text-align "center"}
                                                         "text for an extra line in the table row"]]]
                        [:fo:table-row {:border-top-style "solid", :border-top-width "0.5pt", :border-top-color "red"}
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "row with"]]
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "red top"]]
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "border"]]
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "text for an extra line in the table row"]]]
                        [:fo:table-row {:border-right-style "solid", :border-right-width "0.5pt", :border-right-color "blue"}
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "row with"]]
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "blue right"]]
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "border"]]
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "text for an extra line in the table row"]]]
                        [:fo:table-row {:border-bottom-style "solid", :border-bottom-width "0.5pt", :border-bottom-color "yellow"}
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "row with"]]
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "yellow bottom"]]
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "border"]]
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "text for an extra line in the table row"]]]
                        [:fo:table-row {:border-style "solid", :border-width "0.5pt", :border-color "purple"}
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "row with"]]
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "all"]]
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "border"]]
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "text for an extra line in the table row"]]]]]
                      [:fo:block {:space-after.optimum "15pt", :space-before.optimum "30pt"}
                       "Table 3: column borders"]
                      [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "collapse"}
                       [:fo:table-column {:border-left-style "solid", :border-left-width "0.5pt", :border-left-color "green", :column-width "3cm"} nil]
                       [:fo:table-column {:border-top-style "solid", :border-top-width "0.5pt", :border-top-color "red", :column-width "3cm"} nil]
                       [:fo:table-column {:border-right-style "solid", :border-right-width "0.5pt", :border-right-color "blue", :column-width "3cm"} nil]
                       [:fo:table-column {:border-bottom-style "solid", :border-bottom-width "0.5pt", :border-bottom-color "yellow", :column-width "3cm"} nil]
                       [:fo:table-column {:border-style "solid", :border-width "0.5pt", :border-color "orange", :column-width "3cm"} nil]
                       [:fo:table-body [:fo:table-row [:fo:table-cell [:fo:block {:text-align "center"}
                                                                       "table columns"]]
                                        [:fo:table-cell [:fo:block {:text-align "center"}
                                                         "with"]]
                                        [:fo:table-cell [:fo:block {:text-align "center"}
                                                         "different"]]
                                        [:fo:table-cell [:fo:block {:text-align "center"}
                                                         "borders"]]
                                        [:fo:table-cell [:fo:block {:text-align "center"}
                                                         "text for an extra line in the table row"]]]
                        [:fo:table-row [:fo:table-cell [:fo:block {:text-align "center"}
                                                        "extra"]]
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "table row"]]
                         [:fo:table-cell [:fo:block {:text-align "center"}]]
                         [:fo:table-cell [:fo:block {:text-align "center"}]]
                         [:fo:table-cell [:fo:block {:text-align "center"}
                                          "text for an extra line in the table row"]]]]]
                      ]]])
          serialized-doc (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"(serialize-dom fop-doc))
          results (diff
                   (parse
                    (java.io.ByteArrayInputStream. 
                     (.getBytes serialized-doc)))
                   (parse "test/fo/table-borders-max-ram.fo"))]
                                        ;serialized-doc
      ;results
      (is (and (nil? (first results))
               (nil? (second results)))))))

(deftest pdf-test
  (testing "basic FOP document written out to PDF"
    (is (= (let [test-file "resultdom2pdf.pdf"
                 base-dir (File. ".")
                 out-dir (File. base-dir "out")
                 _ (.mkdirs out-dir)
                 pdf-file (File. out-dir test-file)]
             (convert-dom->pdf
              (->fop [:fo:block
                      {:keep-together.within-page "always"}
                      "garland texas"]) pdf-file)
             (->> pdf-file PDDocument/load (.getText (PDFTextStripper.))))
           "garland texas\n")
        )))
