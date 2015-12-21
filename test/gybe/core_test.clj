(ns gybe.core-test
  (:require [clojure.test :refer :all]
            [gybe.core :refer :all])
  (:import [java.io File]))

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
  (testing "can create "
    (is (= (serialize-dom
            (->fop
             [:fo:box {:keep-together.within-page "always"} "garland texas"]))
           "<fo:root xmlns:fo=\"http://www.w3.org/1999/XSL/Format\"><fo:layout-master-set><fo:simple-page-master margin-bottom=\"1in\" margin-left=\"1in\" margin-right=\"1in\" margin-top=\"1in\" master-name=\"letter\" page-height=\"11in\" page-width=\"8.5in\"><fo:region-body/></fo:simple-page-master></fo:layout-master-set><fo:page-sequence master-reference=\"letter\"><fo:flow flow-name=\"xsl-region-body\"><fo:box keep-together.within-page=\"always\">garland texas</fo:box></fo:flow></fo:page-sequence></fo:root>"))))

#_(deftest pdf-test
  (testing "FIXME, I fail."
    (is (= (let [base-dir (File. ".")
               out-dir (File. base-dir "out")
               _ (.mkdirs out-dir)
               pdf-file (File. out-dir "resultdom2pdf.pdf")]
             (convert-dom->pdf (->dom [:fo:box {:keep-together.within-page "always"} "garland texas"]) pdf-file))
           "pdf-box strip?"))))
