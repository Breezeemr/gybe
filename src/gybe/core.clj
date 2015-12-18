(ns gybe.core
  (:import [java.io File OutputStream FileOutputStream]
           [javax.xml.parsers DocumentBuilder DocumentBuilderFactory ParserConfigurationException]
           [javax.xml.transform Result Source Transformer TransformerFactory]
           [javax.xml.transform.dom DOMSource]
           [javax.xml.transform.sax SAXResult]
           [org.w3c.dom Document Element Node Text]
           [org.apache.fop.apps FOUserAgent Fop FopFactory MimeConstants]))

(def fo-ns "http://www.w3.org/1999/XSL/Format")
(def fop-factory (. FopFactory (newInstance (. (File. ".") toURI))))

(defn serialize-dom [dom]
  (let [ls-impl (.. dom (getOwnerDocument) (getImplementation) (getFeature "LS" "3.0"))
        serializer (.createLSSerializer ls-impl)]
    (.. serializer (getDomConfig) (setParameter "xml-declaration" false))
    (.writeToString serializer dom)))

(defn- create-elem-ns [doc tag attrs content]
  (let [el (. doc (createElementNS fo-ns (name tag)))]
    (assert (map? attrs))
    (doseq [[k v] attrs]
      (.setAttributeNS el nil (name k) v))
    (doseq [item content]
      (if (string? item)
        (.appendChild el (.. el (getOwnerDocument) (createTextNode item)))
        (.appendChild el item)))
    el))

(defn- element-compile-strategy
  "Returns the compilation strategy to use for a given element."
  [doc [tag attrs & content :as element]]
  ;(assert (coll? element))
  (cond
    (string? element) ::string
    (not-any? coll? (rest element)) ::all-literal ; e.g. [:span "foo"]
    (and (keyword? tag) (map? attrs)) ::literal-tag-and-attributes     ; e.g. [:span {} x]
    :else ::default))                      ; e.g. [x]

(defmulti compile-element element-compile-strategy)
(defmethod compile-element ::all-literal
  [doc [tag & content]]
  (prn "all-lit" doc tag content)
  (create-elem-ns doc tag {} content))
(defmethod compile-element ::string
  [doc s]
  s)
(defmethod compile-element ::literal-tag-and-attributes
  [doc [ tag attrs & content]]
  (prn "stuff" doc tag attrs content)
  ;; incorrect, needs to compile content if a vector
  (create-elem-ns doc tag attrs (map (partial compile-element doc) content)))
(defmethod compile-element ::default
  [doc [tag & else]]
  (prn "herp derp default: " tag else)
  (create-elem-ns doc tag {}
    (for [e else]
      (if (vector? e)
        (compile-element doc e)
        e))))

(defn- make-document-builder []
  (let [dbf (DocumentBuilderFactory/newInstance)
        _ (.setNamespaceAware dbf true)
        db (.newDocumentBuilder dbf)]
    db))

(defn ->dom [content & {:keys [root]}]
  (let [db (when (not root) (make-document-builder))
        doc-root (or root (.newDocument db))]

    (.appendChild doc-root (compile-element doc-root content))
    doc-root))

(defn convert-dom->pdf [fo-doc pdf]
  (let [out (FileOutputStream. pdf)
        fo-ua (.newFOUserAgent fop-factory)
        fop (. fop-factory (newFop MimeConstants/MIME_PDF fo-ua out))
        trans-factory (TransformerFactory/newInstance)
        trans (.newTransformer trans-factory)
        src (DOMSource. fo-doc)
        res (SAXResult. (.getDefaultHandler fop))]
    (. trans (transform src res))
    (.close out)))

(defn ->fop [content]
  (->dom [:fo:root
          [:fo:layout-master-set
           [:fo:simple-page-master {:master-name "letter"
                                    :page-height "11in"
                                    :page-width "8.5in"
                                    :margin-top "1in"
                                    :margin-bottom "1in"
                                    :margin-left "1in"
                                    :margin-right "1in"}
            [:fo:region-body]]]
          [:fo:page-sequence {:master-reference "letter"}
           [:fo:flow {:flow-name "xsl-region-body"}
            content]]]))

(comment (convert-dom->pdf
           (->fop [:fo:block {} "hi"]) "test.pdf"))
