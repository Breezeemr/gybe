(ns gybe.core
  (:require [hiccup-to-xml-dom.core :refer [->dom]])
  (:import [java.io File OutputStream FileOutputStream]
           [javax.xml.parsers DocumentBuilder DocumentBuilderFactory ParserConfigurationException]
           [javax.xml.transform Result Source Transformer TransformerFactory]
           [javax.xml.transform.dom DOMSource]
           [javax.xml.transform.sax SAXResult]
           [org.w3c.dom Document Element Node Text]
           [org.apache.fop.apps FOUserAgent Fop FopFactory MimeConstants]))

(def fo-ns "http://www.w3.org/1999/XSL/Format")
(def fop-factory (. FopFactory (newInstance (. (File. ".") toURI))))

(defn convert-dom->pdf
  "takes a byte array output stream and renders a FOP PDF to it"
  [fo-doc out]
  (let [fo-ua (.newFOUserAgent fop-factory)
        fop (. fop-factory (newFop MimeConstants/MIME_PDF fo-ua out))
        trans-factory (TransformerFactory/newInstance)
        trans (.newTransformer trans-factory)
        src (DOMSource. fo-doc)
        res (SAXResult. (.getDefaultHandler fop))]
    (. trans (transform src res))))

(defn ->fop
  [{:keys [page-type page-height page-width margin-top margin-bottom margin-left margin-right doc-ns]
    :or {page-type "letter"
         page-height "11in"
         page-width "8.5in"
         margin-top "1in"
         margin-bottom "1in"
         margin-left "1in"
         margin-right "1in"
         doc-ns fo-ns}}
   & content]
  (->dom [:fo:root
          [:fo:layout-master-set
           [:fo:simple-page-master {:master-name page-type
                                    :page-height page-height
                                    :page-width  page-width
                                    :margin-top  margin-top
                                    :margin-bottom margin-bottom
                                    :margin-left  margin-left
                                    :margin-right margin-right}
            [:fo:region-body]]]
          [:fo:page-sequence {:master-reference "letter"}
           (into [:fo:flow {:flow-name "xsl-region-body"}] content)]]
         :document-namespace doc-ns))
