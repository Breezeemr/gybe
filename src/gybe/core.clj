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
  (let [el (. doc (createElementNS fo-ns (name tag)))
        content (if (seq? content)
                  content
                  (list content))]
    (doseq [[k v] attrs]
      (.setAttributeNS el nil (name k) v))
    (doseq [item content]
      (cond
        (string? item) (.appendChild
                        el
                        (.. el (getOwnerDocument) (createTextNode item)))
        (not (nil? item)) (.appendChild el item)))
    el))

(defn- element-compile-strategy
  "Returns the compilation strategy to use for a given element."
  [[doc tag attrs & content :as element]]
  (cond
    (not-any? coll? (rest element)) ::all-literal ; e.g. [:span "foo"]
    (and (keyword? tag) (map? attrs)) ::literal-tag-and-attributes     ; e.g. [:span {} x]
    :else ::default))                      ; e.g. [x]

(defmulti #^{:private true} compile-element element-compile-strategy)
(defmethod compile-element ::all-literal
  [[doc tag & content]]
  (create-elem-ns doc tag {} content))
(defmethod compile-element ::literal-tag-and-attributes
  [[doc tag attrs & content]]
  (create-elem-ns doc tag attrs
    (map #(if (vector? %)
           (->> % (into [doc]) compile-element)
           %)
      content)))
(defmethod compile-element ::default
  [[doc tag & else]]
  (compile-element
   (into [doc tag]
         (for [e else]
           (if (vector? e)
             (compile-element (into [doc] e))
             e)))))

(defn- make-document-builder []
  (let [dbf (DocumentBuilderFactory/newInstance)
        _ (.setNamespaceAware dbf true)
        db (.newDocumentBuilder dbf)]
    db))

(defn ->dom [content]
  (let [db (make-document-builder)
        doc-root (.newDocument db)]
    (compile-element (into [doc-root] content))))

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

(defn ->fop [& content]
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
           (into [:fo:flow {:flow-name "xsl-region-body"}] content)]]))

(comment
  (def stuff [:fo:root
              [:fo:layout-master-set [:fo:simple-page-master {:page-height "29.7cm", :page-width "21cm", :margin-top "1cm", :margin-bottom "2cm", :margin-left "1.5cm", :margin-right "1.5cm", :master-name "first"}
                                      [:fo:region-body {:margin-top "1cm"}]
                                      [:fo:region-before {:extent "1cm"}]
                                      [:fo:region-after {:extent "1.5cm"}]]]
              [:fo:page-sequence {:master-reference "first"}
               [:fo:static-content {:flow-name "xsl-region-before"}
                [:fo:block {:text-align "end", :font-size "10pt", :line-height "14pt"}
                 "table examples"]]
               [:fo:static-content {:flow-name "xsl-region-after"}
                [:fo:block {:text-align "end", :font-size "10pt", :line-height "14pt"}
                 "Page " [:fo:page-number]]]
               [:fo:flow {:flow-name "xsl-region-body"}
                [:fo:block {:space-after.optimum "15pt", :space-before.optimum "3pt"}
                 "\n\tTable 1: cell borders\n      "]
                [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "separate"}
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "2cm"}]
                 [:fo:table-body [:fo:table-row
                                  [:fo:table-cell {:border-left-style "solid", :border-left-width "0.5pt", :border-left-color "green"}
                                   [:fo:block {:text-align "center"}
                                    "\n\t\tgreen left\n              "]]
                                  [:fo:table-cell {:border-top-style "solid", :border-top-width "0.5pt", :border-top-color "red"}
                                   [:fo:block {:text-align "center"}
                                    "\n\t\tred top\n              "]]
                                  [:fo:table-cell {:border-right-style "solid", :border-right-width "0.5pt", :border-right-color "blue"}
                                   [:fo:block {:text-align "center"}
                                    "\n\t\tblue right\n              "]]
                                  [:fo:table-cell {:border-bottom-style "solid", :border-bottom-width "0.5pt", :border-bottom-color "yellow"}
                                   [:fo:block {:text-align "center"}
                                    "\n\t\tyellow bottom\n              "]]
                                  [:fo:table-cell {:border-top-style "solid", :border-bottom-style "solid", :border-left-width "0.5pt", :border-right-style "solid", :border-left-color "green", :border-right-width "0.5pt", :border-bottom-width "0.5pt", :border-right-color "blue", :border-left-style "solid", :border-top-width "0.5pt", :border-top-color "red", :border-bottom-color "yellow"}
                                   [:fo:block {:text-align "center"}
                                    "\n\t\tall\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\ttext for an extra line in the table row\n              "]]]
                  [:fo:table-row [:fo:table-cell {:border-style "solid", :border-left-width "2pt", :border-color "green"}
                                  [:fo:block {:text-align "center"}
                                   "\n\t\t2pt\n              "]]
                   [:fo:table-cell {:border-top-style "dashed", :border-top-width "2pt", :border-top-color "red"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t2pt\n              "]]
                   [:fo:table-cell {:border-right-style "dotted", :border-right-width "2pt", :border-right-color "blue"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t2pt\n              "]]
                   [:fo:table-cell {:border-bottom-style "double", :border-bottom-width "2pt", :border-bottom-color "yellow"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t2pt\n              "]]
                   [:fo:table-cell {:border-top-style "dashed", :border-bottom-style "dotted", :border-left-width "2pt", :border-right-style "double", :border-left-color "green", :border-right-width "2pt", :border-bottom-width "2pt", :border-right-color "blue", :border-left-style "solid", :border-top-width "2pt", :border-top-color "red", :border-bottom-color "yellow"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t2pt\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]
                  [:fo:table-row [:fo:table-cell {:border-left-style "solid", :border-left-width "10pt", :border-left-color "green"}
                                  [:fo:block {:text-align "center"}
                                   "\n\t\t10pt\n              "]]
                   [:fo:table-cell {:border-top-style "solid", :border-top-width "10pt", :border-top-color "red"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t10pt\n              "]]
                   [:fo:table-cell {:border-right-style "solid", :border-right-width "10pt", :border-right-color "blue"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t10pt\n              "]]
                   [:fo:table-cell {:border-bottom-style "solid", :border-bottom-width "10pt", :border-bottom-color "yellow"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t10pt\n              "]]
                   [:fo:table-cell {:border-top-style "solid", :border-bottom-style "solid", :border-left-width "2pt", :border-right-style "solid", :border-left-color "green", :border-right-width "8pt", :border-bottom-width "10pt", :border-right-color "blue", :border-left-style "solid", :border-top-width "4pt", :border-top-color "red", :border-bottom-color "yellow"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t2pt - 10pt\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]
                  [:fo:table-row [:fo:table-cell {:border-style "solid", :border-width "0.5pt", :border-color "green"}
                                  [:fo:block {:text-align "center"}
                                   "\n\t\t0.5pt\n              "]]
                   [:fo:table-cell {:border-style "solid", :border-width "1pt", :border-color "red"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t1pt\n              "]]
                   [:fo:table-cell {:border-style "solid", :border-width "2pt", :border-color "blue"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t2pt\n              "]]
                   [:fo:table-cell {:border-style "solid", :border-width "10pt", :border-color "yellow"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t10pt\n              "]]
                   [:fo:table-cell {:border-style "solid", :border-width "20pt", :border-color "yellow"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t20pt\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]]]
                [:fo:block {:space-after.optimum "15pt", :space-before.optimum "30pt"}
                 "\n\tTable 2: row borders\n      "]
                [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "collapse"}
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"} nil]
                 [:fo:table-body [:fo:table-row {:border-left-style "solid", :border-left-width "0.5pt", :border-left-color "green"}
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\trow with\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\tgreen left\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\tborder\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\ttext for an extra line in the table row\n              "]]]
                  [:fo:table-row {:border-top-style "solid", :border-top-width "0.5pt", :border-top-color "red"}
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\trow with\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\tred top\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\tborder\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]
                  [:fo:table-row {:border-right-style "solid", :border-right-width "0.5pt", :border-right-color "blue"}
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\trow with\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\tblue right\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\tborder\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]
                  [:fo:table-row {:border-bottom-style "solid", :border-bottom-width "0.5pt", :border-bottom-color "yellow"}
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\trow with\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\tyellow bottom\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\tborder\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]
                  [:fo:table-row {:border-style "solid", :border-width "0.5pt", :border-color "purple"}
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\trow with\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\tall\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\tborder\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]]]
                [:fo:block {:space-after.optimum "15pt", :space-before.optimum "30pt"}
                 "\n\tTable 3: column borders\n      "]
                [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "collapse"}
                 [:fo:table-column {:border-left-style "solid", :border-left-width "0.5pt", :border-left-color "green", :column-width "3cm"} nil]
                 [:fo:table-column {:border-top-style "solid", :border-top-width "0.5pt", :border-top-color "red", :column-width "3cm"} nil]
                 [:fo:table-column {:border-right-style "solid", :border-right-width "0.5pt", :border-right-color "blue", :column-width "3cm"} nil]
                 [:fo:table-column {:border-bottom-style "solid", :border-bottom-width "0.5pt", :border-bottom-color "yellow", :column-width "3cm"} nil]
                 [:fo:table-column {:border-style "solid", :border-width "0.5pt", :border-color "orange", :column-width "3cm"} nil]
                 [:fo:table-body [:fo:table-row [:fo:table-cell [:fo:block {:text-align "center"}
                                                                 "\n\t\ttable columns\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\twith\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\tdifferent\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\tborders\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\ttext for an extra line in the table row\n              "]]]
                  [:fo:table-row [:fo:table-cell [:fo:block {:text-align "center"}
                                                  "\n\t\textra\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttable row\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}]]
                   [:fo:table-cell [:fo:block {:text-align "center"}]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]]]
                [:fo:block {:space-after.optimum "15pt", :space-before.optimum "30pt"}
                 "\n\tTable 4: column borders over page\n      "]
                [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "collapse"}
                 [:fo:table-column {:border-left-style "solid", :border-left-width "0.5pt", :border-left-color "green", :column-width "3cm"}]
                 [:fo:table-column {:border-top-style "solid", :border-top-width "0.5pt", :border-top-color "red", :column-width "3cm"}]
                 [:fo:table-column {:border-right-style "solid", :border-right-width "0.5pt", :border-right-color "blue", :column-width "3cm"}]
                 [:fo:table-column {:border-bottom-style "solid", :border-bottom-width "0.5pt", :border-bottom-color "yellow", :column-width "3cm"}]
                 [:fo:table-column {:border-style "solid", :border-width "0.5pt", :border-color "orange", :column-width "3cm"}]
                 [:fo:table-body [:fo:table-row [:fo:table-cell [:fo:block {:text-align "center"}
                                                                 "\n\t\ttable columns\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\twith\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\tdifferent\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\tborders\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\ttext for an extra line in the table row\n              "]]]
                  [:fo:table-row [:fo:table-cell [:fo:block {:text-align "center"}
                                                  "\n\t\textra\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttable row\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}]]
                   [:fo:table-cell [:fo:block {:text-align "center"}]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]]]
                [:fo:block {:space-after.optimum "15pt", :space-before.optimum "30pt"}
                 "\n\tTable 5: body borders\n      "]
                [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "separate"}
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"} nil]
                 [:fo:table-column {:column-width "3cm"} nil]
                 [:fo:table-column {:column-width "3cm"} nil]
                 [:fo:table-column {:column-width "3cm"} nil]
                 [:fo:table-body {:border-left-style "solid", :border-left-width "0.5pt", :border-left-color "green"}
                  [:fo:table-row [:fo:table-cell [:fo:block {:text-align "center"}
                                                  "\n\t\tbody with\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\tleft border\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}]]
                   [:fo:table-cell [:fo:block {:text-align "center"}]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]]]
                [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "separate"}
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-body {:border-top-style "solid", :border-top-width "0.5pt", :border-top-color "red"}
                  [:fo:table-row [:fo:table-cell [:fo:block {:text-align "center"}
                                                  "\n\t\tbody with\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttop border\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}]]
                   [:fo:table-cell [:fo:block {:text-align "center"}]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]]]
                [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "separate"}
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-body {:border-right-style "solid", :border-right-width "0.5pt", :border-right-color "blue"}
                  [:fo:table-row [:fo:table-cell [:fo:block {:text-align "center"}
                                                  "\n\t\tbody with\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\tright border\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}]]
                   [:fo:table-cell [:fo:block {:text-align "center"}]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]]]
                [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "separate"}
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-body {:border-bottom-style "solid", :border-bottom-width "0.5pt", :border-bottom-color "yellow"}
                  [:fo:table-row [:fo:table-cell [:fo:block {:text-align "center"}
                                                  "\n\t\tbody with\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\tbottom border\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}]]
                   [:fo:table-cell [:fo:block {:text-align "center"}]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]]]
                [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "separate"}
                 [:fo:table-column {:column-width "3cm"} nil]
                 [:fo:table-column {:column-width "3cm"} nil]
                 [:fo:table-column {:column-width "3cm"} nil]
                 [:fo:table-column {:column-width "3cm"} nil]
                 [:fo:table-column {:column-width "3cm"} nil]
                 [:fo:table-body {:border-style "solid", :border-width "0.5pt", :border-color "orange"}
                  [:fo:table-row [:fo:table-cell [:fo:block {:text-align "center"}
                                                  "\n\t\tbody with\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\tall border\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}]]
                   [:fo:table-cell [:fo:block {:text-align "center"}]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]]]
                [:fo:block {:space-after.optimum "15pt", :space-before.optimum "30pt"}
                 "\n\tTable 6: table borders\n      "]
                [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "separate", :border-left-style "solid", :border-left-width "0.5pt", :border-left-color "green"}
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-body [:fo:table-row [:fo:table-cell [:fo:block {:text-align "center"}
                                                                 "\n\t\ttable with\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\tleft border\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\ttext for an extra line in the table row\n              "]]]]]
                [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "separate", :border-top-style "solid", :border-top-width "0.5pt", :border-top-color "red"}
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-body [:fo:table-row [:fo:table-cell [:fo:block {:text-align "center"}
                                                                 "\n\t\ttable with\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\ttop border\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\ttext for an extra line in the table row\n              "]]]]]
                [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "separate", :border-right-style "solid", :border-right-width "0.5pt", :border-right-color "blue"}
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-body [:fo:table-row [:fo:table-cell [:fo:block {:text-align "center"}
                                                                 "\n\t\ttable with\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\tright border\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\ttext for an extra line in the table row\n              "]]]]]
                [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "separate", :border-bottom-style "solid", :border-bottom-width "0.5pt", :border-bottom-color "yellow"}
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-body [:fo:table-row [:fo:table-cell [:fo:block {:text-align "center"}
                                                                 "\n\t\ttable with\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\tbottom border\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\ttext for an extra line in the table row\n              "]]]]]
                [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "separate", :border-style "solid", :border-width "0.5pt", :border-color "orange"}
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-body [:fo:table-row [:fo:table-cell [:fo:block {:text-align "center"}
                                                                 "\n\t\ttable with\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\tall border\n              "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\ttext for an extra line in the table row\n              "]]]]]
                [:fo:block {:space-after.optimum "15pt", :space-before.optimum "30pt"}
                 "\n\tTable 7: combinations\n      "]
                [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "collapse", :border-left-style "solid", :border-left-width "0.5pt", :border-left-color "green"}
                 [:fo:table-column {:column-width "2.5cm"}]
                 [:fo:table-column {:border-left-style "solid", :border-left-width "0.5pt", :border-left-color "green", :column-width "2.5cm"}]
                 [:fo:table-column {:border-top-style "solid", :border-top-width "0.5pt", :border-top-color "red", :column-width "2.5cm"}]
                 [:fo:table-column {:border-right-style "solid", :border-right-width "0.5pt", :border-right-color "blue", :column-width "2.5cm"}]
                 [:fo:table-column {:border-bottom-style "solid", :border-bottom-width "0.5pt", :border-bottom-color "yellow", :column-width "2.5cm"}]
                 [:fo:table-column {:border-style "solid", :border-width "0.5pt", :border-color "orange", :column-width "2.5cm"} nil]
                 [:fo:table-body {:border-style "solid", :border-width "0.5pt", :border-color "aqua"}
                  [:fo:table-row {:border-left-style "solid", :border-left-width "0.5pt", :border-left-color "green"}
                   [:fo:table-cell {:border-left-style "solid", :border-left-width "0.5pt", :border-left-color "green"}
                    [:fo:block {:text-align "center"}
                     "\n\t\ta\n              "]]
                   [:fo:table-cell {:border-top-style "solid", :border-top-width "0.5pt", :border-top-color "red"}
                    [:fo:block {:text-align "center"}
                     "\n\t\tb\n              "]]
                   [:fo:table-cell {:border-right-style "solid", :border-right-width "0.5pt", :border-right-color "blue"}
                    [:fo:block {:text-align "center"}
                     "\n\t\tc\n              "]]
                   [:fo:table-cell {:border-bottom-style "solid", :border-bottom-width "0.5pt", :border-bottom-color "yellow"}
                    [:fo:block {:text-align "center"}
                     "\n\t\td\n              "]]
                   [:fo:table-cell {:border-style "solid", :border-width "0.5pt", :border-color "orange"}
                    [:fo:block {:text-align "center"}
                     "\n\t\te\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]
                  [:fo:table-row {:border-top-style "solid", :border-top-width "0.5pt", :border-top-color "red"}
                   [:fo:table-cell {:border-left-style "solid", :border-left-width "0.5pt", :border-left-color "green"}
                    [:fo:block {:text-align "center"}
                     "\n\t\ta\n              "]]
                   [:fo:table-cell {:border-top-style "solid", :border-top-width "0.5pt", :border-top-color "red"}
                    [:fo:block {:text-align "center"}
                     "\n\t\tb\n              "]]
                   [:fo:table-cell {:border-right-style "solid", :border-right-width "0.5pt", :border-right-color "blue"}
                    [:fo:block {:text-align "center"}
                     "\n\t\tc\n              "]]
                   [:fo:table-cell {:border-bottom-style "solid", :border-bottom-width "0.5pt", :border-bottom-color "yellow"}
                    [:fo:block {:text-align "center"}
                     "\n\t\td\n              "]]
                   [:fo:table-cell {:border-style "solid", :border-width "0.5pt", :border-color "orange"}
                    [:fo:block {:text-align "center"}
                     "\n\t\te\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]
                  [:fo:table-row {:border-right-style "solid", :border-right-width "0.5pt", :border-right-color "blue"}
                   [:fo:table-cell {:border-left-style "solid", :border-left-width "0.5pt", :border-left-color "green"}
                    [:fo:block {:text-align "center"}
                     "\n\t\ta\n              "]]
                   [:fo:table-cell {:border-top-style "solid", :border-top-width "0.5pt", :border-top-color "red"}
                    [:fo:block {:text-align "center"}
                     "\n\t\tb\n              "]]
                   [:fo:table-cell {:border-right-style "solid", :border-right-width "0.5pt", :border-right-color "blue"}
                    [:fo:block {:text-align "center"}
                     "\n\t\tc\n              "]]
                   [:fo:table-cell {:border-bottom-style "solid", :border-bottom-width "0.5pt", :border-bottom-color "yellow"}
                    [:fo:block {:text-align "center"}
                     "\n\t\td\n              "]]
                   [:fo:table-cell {:border-style "solid", :border-width "0.5pt", :border-color "orange"}
                    [:fo:block {:text-align "center"}
                     "\n\t\te\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]
                  [:fo:table-row {:border-bottom-style "solid", :border-bottom-width "0.5pt", :border-bottom-color "yellow"}
                   [:fo:table-cell {:border-left-style "solid", :border-left-width "0.5pt", :border-left-color "green"}
                    [:fo:block {:text-align "center"}
                     "\n\t\ta\n              "]]
                   [:fo:table-cell {:border-top-style "solid", :border-top-width "0.5pt", :border-top-color "red"}
                    [:fo:block {:text-align "center"}
                     "\n\t\tb\n              "]]
                   [:fo:table-cell {:border-right-style "solid", :border-right-width "0.5pt", :border-right-color "blue"}
                    [:fo:block {:text-align "center"}
                     "\n\t\tc\n              "]]
                   [:fo:table-cell {:border-bottom-style "solid", :border-bottom-width "0.5pt", :border-bottom-color "yellow"}
                    [:fo:block {:text-align "center"}
                     "\n\t\td\n              "]]
                   [:fo:table-cell {:border-style "solid", :border-width "0.5pt", :border-color "orange"}
                    [:fo:block {:text-align "center"}
                     "\n\t\te\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]
                  [:fo:table-row {:border-style "solid", :border-width "0.5pt", :border-color "orange"}
                   [:fo:table-cell {:border-left-style "solid", :border-left-width "0.5pt", :border-left-color "green"}
                    [:fo:block {:text-align "center"}
                     "\n\t\ta\n              "]]
                   [:fo:table-cell {:border-top-style "solid", :border-top-width "0.5pt", :border-top-color "red"}
                    [:fo:block {:text-align "center"}
                     "\n\t\tb\n              "]]
                   [:fo:table-cell {:border-right-style "solid", :border-right-width "0.5pt", :border-right-color "blue"}
                    [:fo:block {:text-align "center"}
                     "\n\t\tc\n              "]]
                   [:fo:table-cell {:border-bottom-style "solid", :border-bottom-width "0.5pt", :border-bottom-color "yellow"}
                    [:fo:block {:text-align "center"}
                     "\n\t\td\n              "]]
                   [:fo:table-cell {:border-style "solid", :border-width "0.5pt", :border-color "orange"}
                    [:fo:block {:text-align "center"}
                     "\n\t\te\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]
                  [:fo:table-row [:fo:table-cell {:border-left-style "solid", :border-left-width "0.5pt", :border-left-color "green"}
                                  [:fo:block {:text-align "center"}
                                   "\n\t\ta\n              "]]
                   [:fo:table-cell {:border-top-style "solid", :border-top-width "0.5pt", :border-top-color "red"}
                    [:fo:block {:text-align "center"}
                     "\n\t\tb\n              "]]
                   [:fo:table-cell {:border-right-style "solid", :border-right-width "0.5pt", :border-right-color "blue"}
                    [:fo:block {:text-align "center"}
                     "\n\t\tc\n              "]]
                   [:fo:table-cell {:border-bottom-style "solid", :border-bottom-width "0.5pt", :border-bottom-color "yellow"}
                    [:fo:block {:text-align "center"}
                     "\n\t\td\n              "]]
                   [:fo:table-cell {:border-style "solid", :border-width "0.5pt", :border-color "orange"}
                    [:fo:block {:text-align "center"}
                     "\n\t\te\n              "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\ttext for an extra line in the table row\n              "]]]]]
                [:fo:block {:space-after.optimum "15pt", :space-before.optimum "30pt"}
                 "\n\tTable 8:  This is a table with border properties (border-style,\n\n\tborder-width,\nborder-color) defined at the fo:table-column and \n\tfo:table-row level.  Not all properties are currently \n\timplemented--check the compliance page on the FOP website for current \n\timplementation status.\n      "]
                [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "collapse"}
                 [:fo:table-column {:border-style "solid", :border-width "0.5pt", :border-color "blue", :column-width "3cm"}]
                 [:fo:table-column {:border-style "solid", :border-width "0.5pt", :border-color "blue", :column-width "3cm"}]
                 [:fo:table-column {:border-style "solid", :border-width "0.5pt", :border-color "blue", :column-width "3cm"} nil]
                 [:fo:table-body [:fo:table-row {:border-style "solid", :border-width "0.5pt", :border-color "blue"}
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\t(1,1)\n\t      "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\t(1,2)\n\t      "]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "\n\t\t(1,3)\n\t      "]]]
                  [:fo:table-row {:border-style "solid", :border-width "0.5pt", :border-color "blue"}
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\t(2,1)\n\t      "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\t(2,2)\n\t      "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\t(2,3)\n\t      "]]]
                  [:fo:table-row {:border-style "solid", :border-width "0.5pt", :border-color "blue"}
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\t(3,1)\n\t      "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\t(3,2)\n\t      "]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "\n\t\t(3,3)\n\t      "]]]]]
                [:fo:block {:space-after.optimum "15pt", :space-before.optimum "30pt"}
                 "\n\tTable 9: This table has border properties defined at the \n\tfo:table-cell level.\n      "]
                [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "collapse"}
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-body [:fo:table-row [:fo:table-cell {:border-style "solid", :border-width "0.5pt", :border-color "blue"}
                                                 [:fo:block {:text-align "center"}
                                                  "\n\t\t(1,1)\n\t      "]]
                                  [:fo:table-cell {:border-style "solid", :border-width "0.5pt", :border-color "blue"}
                                   [:fo:block {:text-align "center"}
                                    "\n\t\t(1,2)\n\t      "]]
                                  [:fo:table-cell {:border-style "solid", :border-width "0.5pt", :border-color "blue"}
                                   [:fo:block {:text-align "center"}
                                    "\n\t\t(1,3)\n\t      "]]]
                  [:fo:table-row [:fo:table-cell {:border-style "solid", :border-width "0.5pt", :border-color "blue"}
                                  [:fo:block {:text-align "center"}
                                   "\n\t\t(2,1)\n\t      "]]
                   [:fo:table-cell {:border-style "solid", :border-width "0.5pt", :border-color "blue"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t(2,2)\n\t      "]]
                   [:fo:table-cell {:border-style "solid", :border-width "0.5pt", :border-color "blue"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t(2,3)\n\t      "]]]
                  [:fo:table-row [:fo:table-cell {:border-style "solid", :border-width "0.5pt", :border-color "blue"}
                                  [:fo:block {:text-align "center"}
                                   "\n\t\t(3,1)\n\t      "]]
                   [:fo:table-cell {:border-style "solid", :border-width "0.5pt", :border-color "blue"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t(3,2)\n\t      "]]
                   [:fo:table-cell {:border-style "solid", :border-width "0.5pt", :border-color "blue"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t(3,3)\n\t      "]]]]]
                [:fo:block {:space-after.optimum "15pt", :space-before.optimum "30pt"}
                 "\n\tTable 10: This example is the first example given in the CSS2 border conflict \n\tresolution rules.  \n\t(See http://www.w3.org/TR/REC-CSS2/tables.html#border-conflict-resolution)\n\tWhen all properties are resolved and implemented,\nthe table below should\n\tresemble the one shown in this section of the CSS2 specification. \n\tCheck the FOP compliance page for current implementation status.\n      "]
                [:fo:table {:border-color "yellow", :border-width "5pt", :border-style "solid", :width "100%", :table-layout "fixed", :border-collapse "collapse"}
                 [:fo:table-column {:border-color "black", :border-width "3pt", :border-style "solid", :column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-body [:fo:table-row [:fo:table-cell {:padding "1em", :border-color "red", :border-width "1pt", :border-style "solid"}
                                                 [:fo:block {:text-align "center"}
                                                  "\n\t\t1\n\t      "]]
                                  [:fo:table-cell {:padding "1em", :border-color "red", :border-width "1pt", :border-style "solid"}
                                   [:fo:block {:text-align "center"}
                                    "\n\t\t2\n\t      "]]
                                  [:fo:table-cell {:padding "1em", :border-color "red", :border-width "1pt", :border-style "solid"}
                                   [:fo:block {:text-align "center"}
                                    "\n\t\t3\n\t      "]]]
                  [:fo:table-row [:fo:table-cell {:padding "1em", :border-color "red", :border-width "1pt", :border-style "solid"}
                                  [:fo:block {:text-align "center"}
                                   "\n\t\t4\n\t      "]]
                   [:fo:table-cell {:padding "1em", :border-color "blue", :border-width "5pt", :border-style "dashed"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t5\n\t      "]]
                   [:fo:table-cell {:padding "1em", :border-color "green", :border-width "5pt", :border-style "solid"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t6\n\t      "]]]
                  [:fo:table-row [:fo:table-cell {:padding "1em", :border-color "red", :border-width "1pt", :border-style "solid"}
                                  [:fo:block {:text-align "center"}
                                   "\n\t\t7\n\t      "]]
                   [:fo:table-cell {:padding "1em", :border-color "red", :border-width "1pt", :border-style "solid"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t8\n\t      "]]
                   [:fo:table-cell {:padding "1em", :border-color "red", :border-width "1pt", :border-style "solid"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t9\n\t      "]]]
                  [:fo:table-row [:fo:table-cell {:padding "1em", :border-color "red", :border-width "1pt", :border-style "solid"}
                                  [:fo:block {:text-align "center"}
                                   "\n\t\t10\n\t      "]]
                   [:fo:table-cell {:padding "1em", :border-color "red", :border-width "1pt", :border-style "solid"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t11\n\t      "]]
                   [:fo:table-cell {:padding "1em", :border-color "red", :border-width "1pt", :border-style "solid"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t12\n\t      "]]]
                  [:fo:table-row [:fo:table-cell {:padding "1em", :border-color "red", :border-width "1pt", :border-style "solid"}
                                  [:fo:block {:text-align "center"}
                                   "\n\t\t13\n\t      "]]
                   [:fo:table-cell {:padding "1em", :border-color "red", :border-width "1pt", :border-style "solid"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t14\n\t      "]]
                   [:fo:table-cell {:padding "1em", :border-color "red", :border-width "1pt", :border-style "solid"}
                    [:fo:block {:text-align "center"}
                     "\n\t\t15\n\t      "]]]]]
                [:fo:block {:space-after.optimum "15pt", :space-before.optimum "30pt"}
                 "\n\tTable 11: This example is a test of Rule 4 of the CSS2 border conflict \n\tresolution rules.\n\t(See http://www.w3.org/TR/REC-CSS2/tables.html#border-conflict-resolution)\n\tThis rule gives the order of precedence of resolution to be cell (highest),\n\n\tthen row,\nthen column,\nthen table (lowest),\nin those cases where\n\tthe border properties differ only on color.\n      "]
                [:fo:table {:width "100%", :table-layout "fixed", :border-collapse "collapse"}
                 [:fo:table-column {:border-color "black", :border-width "3pt", :border-style "solid", :column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:column-width "3cm"}]
                 [:fo:table-column {:border-color "black", :border-width "3pt", :border-style "solid", :column-width "3cm"}]
                 [:fo:table-body [:fo:table-row {:border-color "red", :border-width "3pt", :border-style "solid"}
                                  [:fo:table-cell {:border-color "blue", :border-width "3pt", :border-style "solid"}
                                   [:fo:block {:text-align "center"}
                                    "cell,\nrow,\ncol"]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "row"]]
                                  [:fo:table-cell {:border-color "blue", :border-width "3pt", :border-style "solid"}
                                   [:fo:block {:text-align "center"}
                                    "cell,\nrow"]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "row"]]
                                  [:fo:table-cell [:fo:block {:text-align "center"}
                                                   "row,\ncol"]]]
                  [:fo:table-row [:fo:table-cell [:fo:block {:text-align "center"}
                                                  "col"]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "none"]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "none"]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "none"]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "col"]]]
                  [:fo:table-row [:fo:table-cell {:border-color "blue", :border-width "3pt", :border-style "solid"}
                                  [:fo:block {:text-align "center"}
                                   "cell,\ncol"]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "none"]]
                   [:fo:table-cell {:border-color "blue", :border-width "3pt", :border-style "solid"}
                    [:fo:block {:text-align "center"}
                     "cell"]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "none"]]
                   [:fo:table-cell [:fo:block {:text-align "center"}
                                    "col"]]]]]]]]))