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
  
  (testing "more complex FOP document"
    (let [results (diff
                   (parse
                    (java.io.ByteArrayInputStream. 
                     (.getBytes
                      (serialize-dom
                       (->fop
                        [:fo:block {:space-before.optimum "3pt"
                                    :space-after.optimum "15pt"}
                         "Table 1: cell borders"]
                        (into [:fo:table {:border-collapse "separate"
                                          :table-layout "fixed"
                                          :width "100%"}]
                              (repeat 6 [:fo:table-column {:column-width "3cm"}]))
                        [:fo:table-body
                         [:fo:table-row
                          [:fo:table-cell {:border-left-color "green"
                                           :border-left-width "0.5pt"
                                           :border-left-style "solid"}
                           [:fo:block {:text-align "center"} "green left"]]]])))))
                   (parse "test/fo/table-borders.fo"))]
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
