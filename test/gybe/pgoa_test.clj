(ns gybe.core-test
  (:require [clojure.test :refer :all]
            [gybe.core :refer :all]
            [clojure.string :as s]
            [hiccup-to-xml-dom.core :refer [serialize-dom ->dom]]
            [clojure.data :refer [diff]]
            [clojure.xml :refer [parse]])
  (:import [java.io File FileOutputStream]
           [javax.xml.bind DatatypeConverter]
           [java.time Instant ZoneId LocalDateTime]
           [java.time.format DateTimeFormatter]
           [java.util Date]
           [org.apache.pdfbox.util PDFTextStripper]
           [org.apache.pdfbox.pdmodel PDDocument]))

(def print-birthdate-tz "Etc/UTC")
(def         base-bottom-margin                            0.6)

(def make-inch #(str % "in"))
(def fp-top-margin                                 1.2)

(defn statement-table [& {:keys [statement-date pay-amount account-number]}]
  [:fo:table {:width "100%"
              :table-layout "fixed"
              ;:border-color              "blue"
              ;:border-style              "solid"
              ;:border-width              "1pt"
              }
   [:fo:table-column {:column-width "33%"}]
   [:fo:table-column {:column-width "33%"}]
   [:fo:table-column {:column-width "33%"}]
   [:fo:table-body
    [:fo:table-row
     [:fo:table-cell [:fo:block {:font-weight "bold" :font-size "5.5pt"
                                 :color "blue"
                                 :padding-left "0.1in"
                                 :padding-right "0.1in"
                                 :background-color "#61A8D6"
                                 :text-align "center" :border "1pt solid blue"}
                      "STATEMENT DATE"]]
     [:fo:table-cell [:fo:block {:font-weight "bold" :font-size "5.5pt"
                                 :color "blue"
                                 :padding-left "0.1in"
                                 :padding-right "0.1in"
                                 :border-left "1pt solid blue"
                                 :background-color "#61A8D6"
                                 :text-align "center" :border "1pt solid blue"}
                      "PAY THIS AMMOUNT"]]
     [:fo:table-cell [:fo:block {:font-weight "bold" :font-size "5.5pt"
                                 :color "blue"
                                 :padding-left "0.1in"
                                 :padding-right "0.1in"
                                 :border-left "1pt solid blue"
                                 :background-color "#61A8D6"
                                 :text-align "center" :border "1pt solid blue"}
                      "ACCOUNT NO."]]]
    [:fo:table-row
     [:fo:table-cell [:fo:block {:font-weight "medium" :font-size "6.5pt"
                                 :color "black"
                                 :padding "0.1in"
                                 :border-left "1pt solid blue"
                                 :border-bottom "1pt solid blue"                                 
                                 :text-align "center"}
                      statement-date]]
     [:fo:table-cell [:fo:block {:font-weight "medium" :font-size "6.5pt"
                                 :color "black"
                                 :padding "0.1in"
                                 :border-left "1pt solid blue"
                                 :border-bottom "1pt solid blue"
                                 :text-align "center"}
                      pay-amount]]
     [:fo:table-cell [:fo:block {:font-weight "medium" :font-size "6.5pt"
                                 :color "black"
                                 :padding "0.1in"
                                 :border-left "1pt solid blue"
                                 :border-right "1pt solid blue"
                                 :border-bottom "1pt solid blue"
                                 :text-align "center"}
                      account-number]]]
    [:fo:table-row
     [:fo:table-cell {:number-columns-spanned "2"}
      [:fo:block {:font-weight "medium"
                  :font-size "6pt"
                  :color "red"
                  :padding "0.1in"
                  :text-align "center"}
       "Charges and credits made after statement date will appear on next statement"]]
     [:fo:table-cell 
      [:fo:block {:font-weight "bold"
                  :font-size "7pt"
                  :color "black"
                  :padding "0.1in"
                  :border-left "1pt solid blue"
                  :border-right "1pt solid blue"
                  :border-bottom "1pt solid blue"
                  :text-align "left"}
       [:fo:table {}
        [:fo:table-column {:column-width "50%"}]
        [:fo:table-column {:column-width "50%"}]
        [:fo:table-body
         [:fo:table-row
          [:fo:table-cell
           [:fo:block
            "Show amount paid here"]]
          [:fo:table-cell
           [:fo:block {:font-weight "bold"
                       :font-size "11pt"
                       :color "black"
                       :padding-left "0.2in"
                       :text-align "left"}
            "$"]]]]]]]]]])

(def credit-card-form
  (let [
        mastercard-b64 (. DatatypeConverter
                          (printBase64Binary
                           (-> "MasterCard_logo.png"
                               clojure.java.io/resource
                               file->bytes)))
        visa-b64 (. DatatypeConverter
                    (printBase64Binary
                     (-> "visa-logo.png"
                         clojure.java.io/resource
                         file->bytes)))
        discover-b64 (. DatatypeConverter
                        (printBase64Binary
                         (-> "Discover-Card-01.png"
                             clojure.java.io/resource
                             file->bytes)))]
   [:fo:table {:width "100%"
               :table-layout "fixed"
               :border-color              "black"
               :border-style              "solid"
               :border-width              "1pt"}
    [:fo:table-column {:column-width "60%"}]
    [:fo:table-column {:column-width "10%"}]
    [:fo:table-column {:column-width "30%"}]
    [:fo:table-body
     [:fo:table-row
      [:fo:table-cell {:number-columns-spanned "3"}
       [:fo:block {:font-weight "bold" :font-size "4pt" :text-align "center" :border-bottom "1pt solid #000000"}
        "IF PAYING BY VISA, MASTERCARD OR DISCOVER, FILL OUT BELOW"]]]
     [:fo:table-row
      [:fo:table-cell {:number-columns-spanned "3"}
       [:fo:table
        [:fo:table-column {:column-width "33%"}]
        [:fo:table-column {:column-width "33%"}]
        [:fo:table-column {:column-width "33%"}]
        [:fo:table-body
         [:fo:table-row
          [:fo:table-cell
           [:fo:block
            {:font-weight "medium" :font-size "4pt" :text-align "center" :border-bottom "1pt solid #000000"}
            #_[:fo:instream-foreign-object
               [:svg:svg {:xmlns:svg "http://www.w3.org/2000/svg" :width "5" :height "5"}
                [:svg:rect {:width "5" :height "5" :style "stroke-width:0.5;stroke:rgb(0,0,0)"}]]]
            [:fo:inline {:font-family "Arial Unicode MS" :font-size "7pt" :padding-right "2pt"
                         :color "#ffffff" :border "1pt solid #000000"} (str \u2611)]
            "   VISA"
            [:fo:external-graphic {:src            (str "data:image/png;base64," visa-b64)
                                   :width          (make-inch 0.35) :height (make-inch 0.15)
                                   :content-height "scale-down-to-fit" 
                                   :content-width  "scale-down-to-fit"}]]]
          [:fo:table-cell
           [:fo:block
            {:font-weight "medium" :font-size "4pt" :text-align "center" :border-bottom "1pt solid #000000"}
            [:fo:inline {:font-family "Arial Unicode MS" :font-size "7pt" :padding-right "2pt"
                         :color "#ffffff" :border "1pt solid #000000"} (str \u2611)]
            "   MASTERCARD"
            [:fo:external-graphic {:src            (str "data:image/png;base64," mastercard-b64)
                                   :width          (make-inch 0.3) :height (make-inch 0.15)
                                   :content-height "scale-down-to-fit" 
                                   :content-width  "scale-down-to-fit"}]]]
          [:fo:table-cell
           [:fo:block
            {:font-weight "medium" :font-size "4pt" :text-align "center" :border-bottom "1pt solid #000000"}
            [:fo:inline {:font-family "Arial Unicode MS" :font-size "7pt" :padding-right "2pt"
                         :color "#ffffff" :border "1pt solid #000000"} (str \u2611)]
            "   DISCOVER"
            [:fo:external-graphic {:src            (str "data:image/png;base64," discover-b64)
                                   :width          (make-inch 0.3) :height (make-inch 0.15)
                                   :content-height "scale-down-to-fit" 
                                   :content-width  "scale-down-to-fit"}]
            ]]]]]]]
     [:fo:table-row
      [:fo:table-cell [:fo:block {:font-weight "medium"
                                  :font-size "4pt"
                                  :text-align "left"
                                  :padding-bottom "0.15in"
                                  :border-bottom "1pt solid #000000"}
                       "CARD NUMBER"]]
      [:fo:table-cell [:fo:block {:font-weight "medium"
                                  :font-size "4pt"
                                  :text-align "left"
                                  :padding-bottom "0.15in"
                                  :border-left "1pt solid #000000"
                                  :border-bottom "1pt solid #000000"}
                       "EXP. DATE"]]
      [:fo:table-cell [:fo:block {:font-weight "medium"
                                  :font-size "4pt"
                                  :text-align "left"
                                  :padding-bottom "0.15in"
                                  :border-left "1pt solid #000000"
                                  :border-bottom "1pt solid #000000"}
                       "AMOUNT"]]]
     [:fo:table-row
      [:fo:table-cell [:fo:block {:font-weight "medium"
                                  :font-size "4pt"
                                  :text-align "left"
                                  :padding-bottom "0.15in"}
                       "SIGNATURE"]]
      [:fo:table-cell {:number-columns-spanned "2"
                       :border-left "1pt solid #000000"}
       [:fo:block {:font-weight "medium"
                   :font-size "5.5pt"
                   :text-align "left"}
        "MUST INCLUDE 3 DIGIT SECURITY CODE FROM BACK OF CARD"]]
      ]]]))

(defn pgoa-billing-header [& {:keys [pgoa-address billing-questions-phone-number pgoa-fax-number pgoa-website
                                     patient-address]
                              :or {pgoa-address
                                   {:title "Pediatric Group of Acadiana, LLC"
                                    :street "2308 E. Main Street, Suite G"
                                    :city "New Iberia" :state "LA" :zip "70560"}
                                   billing-questions-phone-number "337-367-2001 Option 5"
                                   pgoa-fax-number "337-321-6295"
                                   pgoa-website "www.pgacadiana.com"}}]
  [:fo:table {;:width        "100%"
              :table-layout "fixed"}
   [:fo:table-column {:column-width "50%"}]
   [:fo:table-column {:column-width "50%"}]
   [:fo:table-body
    [:fo:table-row {:height "0.75in"
                    :keep-together.within-column "always"
                    :padding-bottom "0.25in"
                    :space-after "0.25in"}
     [:fo:table-cell {:font-weight "medium" :font-size "7.5pt" :padding-bottom "5pt"
                      :border-style "none"}
      [:fo:block {:text-align "left"} (:title pgoa-address)]
      [:fo:block {:text-align "left"} (:street pgoa-address)]
      [:fo:block {:text-align "left"} ((fn [{:keys [city state zip]}]
                                         (str city ", " state " " zip))
                                       pgoa-address)]]
     [:fo:table-cell [:fo:block credit-card-form]]]
    [:fo:table-row {:padding-bottom "0.25in"}
     [:fo:table-cell {:font-weight "medium" :font-size "6.5pt" :padding-bottom "5pt"}
      [:fo:block {:text-align "left"} (str "For all billing questions, call: " billing-questions-phone-number)]
      [:fo:block {:text-align "left"} (str "Fax: " pgoa-fax-number)]
      [:fo:block {:text-align "left"} (str "Website: " pgoa-website)]]
     [:fo:table-cell [:fo:block (statement-table :statement-date "2018-09-08"
                                                 :pay-amount "Continued"
                                                 :account-number "131442 133041")]]]
    [:fo:table-row {:padding-top "0.25in"}
     [:fo:table-cell [:fo:block ""]]
     [:fo:table-cell {:font-weight "bold" :font-size "6pt" :color "blue" :text-align "left"}
      [:fo:block "MAKE CHECKS PAYABLE/REMIT TO:"]]]
    [:fo:table-row {:padding-bottom  "0.25in"}
     [:fo:table-cell {:font-weight "medium" :font-size "7.5pt" :padding-bottom "5pt"
                      :padding "15pt"
                      :border-style "none"}
      [:fo:block {:text-align "left"} (:title patient-address)]
      [:fo:block {:text-align "left"} (:street patient-address)]
      [:fo:block {:text-align "left"} ((fn [{:keys [city state zip]}]
                                         (str city ", " state " " zip))
                                       patient-address)]]
     [:fo:table-cell {:font-weight "medium" :font-size "7.5pt" :padding-bottom "5pt"
                      :padding "15pt"
                      :border-style "none"}
      [:fo:block {:text-align "left"} (:title pgoa-address)]
      [:fo:block {:text-align "left"} (:street pgoa-address)]
      [:fo:block {:text-align "left"} ((fn [{:keys [city state zip]}]
                                         (str city ", " state " " zip))
                                       pgoa-address)]]]]])

(defn pgoa-billing-static-footer [dior & {:keys [billing-questions-phone-number pgoa-fax-number pgoa-website
                                                 dunning-message]
                                          :or {billing-questions-phone-number "337-367-2001 Option 5"
                                               dunning-message "Continued on next page"
                                               pgoa-fax-number "337-321-6295"
                                               pgoa-website "www.pgacadiana.com"}}]
  (let []
    [:fo:block
     [:fo:table {:width "100%"}
      [:fo:table-column {:column-width "50%"}]
      [:fo:table-column {:column-width "50%"}]
      [:fo:table-body
       [:fo:table-row {:height "1in"}
        [:fo:table-cell {:background-color "pink"}
         [:fo:block dunning-message]]
        [:fo:table-cell
         [:fo:block
          [:fo:table
           [:fo:table-column {:column-width "100%"}]
           [:fo:table-body
            [:fo:table-row
             [:fo:table-cell
              [:fo:block {:font-size "9pt"}
               (str "For all billing questions, call: " billing-questions-phone-number)]]]
            [:fo:table-row [:fo:table-cell [:fo:block {:font-size "9pt"} (str "Fax: " pgoa-fax-number)]]]
            [:fo:table-row [:fo:table-cell [:fo:block {:font-size "9pt"} (str "Website: " pgoa-website)]]]
            ]]]]]]]]))

(defn statement-body [rows & {:keys [age-pay-current age-pay-30 age-pay-60 age-pay-90 total-balance patient-due]
                              :or {patient-due "Continued"}}]
  [:fo:block {:background-color "#DDDDDD"
              :padding "0.15in"
              :margin-top "1.25in"}
   [:fo:table
    [:fo:table-column {:column-width "10%"}]
    [:fo:table-column {:column-width "50%"}]
    [:fo:table-column {:column-width "10%"}]
    [:fo:table-column {:column-width "10%"}]
    [:fo:table-column {:column-width "10%"}]
    [:fo:table-column {:column-width "10%"}]
    (conj
     (into [:fo:table-body {:border-top "1pt solid #000000"
                            :border-right "1pt solid #000000"
                            :border-left "1pt solid #000000"}
            [:fo:table-row {:border-bottom "1pt solid #000000"}
             [:fo:table-cell [:fo:block {:font-weight "bold" :font-size "6.5pt" :padding-bottom "5pt"}
                              "DATE"]]
             [:fo:table-cell [:fo:block {:font-weight "bold" :font-size "6.5pt" :padding-bottom "5pt"}
                              "DESCRIPTION"]]
             [:fo:table-cell [:fo:block {:font-weight "bold" :font-size "6.5pt" :padding-bottom "5pt"}
                              "CHARGE"]]
             [:fo:table-cell [:fo:block {:font-weight "bold" :font-size "6.5pt" :padding-bottom "5pt"}
                              "Patient/Adjustment"]]
             [:fo:table-cell {:number-columns-spanned "2"}
              [:fo:block
               [:fo:table
                [:fo:table-column {:column-width "50%"}]
                [:fo:table-column {:column-width "50%"}]
                [:fo:table-body
                 [:fo:table-row
                  [:fo:table-cell {:number-columns-spanned "2"}
                   [:fo:block {:text-align "center"} "BALANCE"]]]
                 [:fo:table-row
                  [:fo:table-cell [:fo:block {:text-align "center" :font-size "8pt"} "PATIENT"]]
                  [:fo:table-cell [:fo:block {:text-align "center" :font-size "8pt"} "INSURANCE"]]]]]]]]]
           (map (fn [[col-date col-description col-charge col-patient col-balance-patient col-balance-ins]]
                  [:fo:table-row #_{:border-bottom "1pt solid #000000"}
                   [:fo:table-cell [:fo:block {:font-weight "bold"
                                               :font-size "6.5pt"
                                               :padding-bottom "5pt"}
                                    col-date]]
                   [:fo:table-cell [:fo:block {:font-weight "bold"
                                               :font-size "6.5pt"
                                               :border-left "1pt solid #000000"
                                               :padding-bottom "5pt"}
                                    col-description]]
                   [:fo:table-cell [:fo:block {:font-weight "bold"
                                               :font-size "6.5pt"
                                               :border-left "1pt solid #000000"
                                               :padding-bottom "5pt"}
                                    col-charge]]
                   [:fo:table-cell [:fo:block {:font-weight "bold"
                                               :font-size "6.5pt"
                                               :border-left "1pt solid #000000"
                                               :padding-bottom "5pt"}
                                    col-patient]]
                   [:fo:table-cell [:fo:block {:font-weight "bold"
                                               :font-size "6.5pt"
                                               :border-left "1pt solid #000000"
                                               :padding-bottom "5pt"}
                                    col-balance-patient]]
                   [:fo:table-cell [:fo:block {:font-weight "bold"
                                               :font-size "6.5pt"
                                               :border-left "1pt solid #000000"
                                               :padding-bottom "5pt"}
                                    col-balance-ins]]]))
           rows)
     [:fo:table-row
      [:fo:table-cell {:number-columns-spanned "6"}
       [:fo:block
        [:fo:table
         [:fo:table-column {:column-width "10%"}]
         [:fo:table-column {:column-width "10%"}]
         [:fo:table-column {:column-width "10%"}]
         [:fo:table-column {:column-width "10%"}]
         [:fo:table-column {:column-width "20%"}]
         [:fo:table-column {:column-width "20%"}]
         [:fo:table-column {:column-width "20%"}]
         [:fo:table-body
          [:fo:table-row #_{:height "0.25in"}
           [:fo:table-cell [:fo:block {:text-align "center"
                                       :font-size "10pt"
                                       :border-top "1pt solid #000000"
                                       :border-bottom "1pt solid #000000"}
                            "CURRENT"]]
           [:fo:table-cell [:fo:block {:text-align "center"
                                       :font-size "10pt"
                                       :border-top "1pt solid #000000"
                                       :border-left "1pt solid #000000"
                                       :border-bottom "1pt solid #000000"}
                            "30 DAYS"]]
           [:fo:table-cell [:fo:block {:text-align "center"
                                       :font-size "10pt"
                                       :border-top "1pt solid #000000"
                                       :border-left "1pt solid #000000"
                                       :border-bottom "1pt solid #000000"}
                            "60 DAYS"]]
           [:fo:table-cell [:fo:block {:text-align "center"
                                       :font-size "10pt"
                                       :border-top "1pt solid #000000"
                                       :border-left "1pt solid #000000"
                                       :border-right "1pt solid #000000"
                                       :border-bottom "1pt solid #000000"}
                            "90 DAYS"]]
           [:fo:table-cell [:fo:block {:border-top "1pt solid #000000"}]]
           [:fo:table-cell [:fo:block {:text-align "center"
                                       :font-size "10pt"
                                       :border-top "1pt solid #000000"
                                       :border-left "1pt solid #000000"
                                       :border-bottom "1pt solid #000000"} "TOTAL BALANCE"]]
           [:fo:table-cell [:fo:block {:text-align "center"
                                       :font-size "10pt"
                                       :border-top "1pt solid #000000"
                                       :border-left "1pt solid #000000"
                                       :border-bottom "1pt solid #000000"} "PATIENT DUE"]]]
          [:fo:table-row #_{:height "0.25in"}
           [:fo:table-cell [:fo:block {:text-align "center"
                                       :border-bottom "1pt solid #000000"}
                            (if age-pay-current age-pay-current [:fo:leader])]]
           [:fo:table-cell [:fo:block {:text-align "center"
                                       :border-left "1pt solid #000000"
                                       :border-bottom "1pt solid #000000"}
                            (if age-pay-30 age-pay-30 [:fo:leader])]]
           [:fo:table-cell [:fo:block {:text-align "center"
                                       :border-left "1pt solid #000000"
                                       :border-bottom "1pt solid #000000"}
                            (if age-pay-60 age-pay-60 [:fo:leader])]]
           [:fo:table-cell [:fo:block {:text-align "center"
                                       :border-left "1pt solid #000000"
                                       :border-right "1pt solid #000000"
                                       :border-bottom "1pt solid #000000"}
                            (if age-pay-90 age-pay-90 [:fo:leader])]]
           [:fo:table-cell [:fo:block #_{:border-bottom "1pt solid #000000"}]]
           [:fo:table-cell [:fo:block {:text-align "center"
                                       :border-left "1pt solid #000000"
                                       :border-bottom "1pt solid #000000"}
                            (if total-balance total-balance [:fo:leader])]]
           [:fo:table-cell [:fo:block {:text-align "center"
                                       :border-left "1pt solid #000000"
                                       :border-bottom "1pt solid #000000"}
                            patient-due]]]]]]]])]])

(def pgoa-bill-layout
  [:fo:root
   [:fo:layout-master-set
    ;; first page
    [:fo:simple-page-master {:master-name "letter-page-one"
                             :page-height "11in"   :page-width    "8.5in"
                             :margin-top  "0.1in"  :margin-bottom "0.75in"
                             :margin-left "0.3in" :margin-right  "0.3in"}
     [:fo:region-body {:margin-top    (make-inch fp-top-margin)
                       :margin-bottom (make-inch base-bottom-margin)}]
     [:fo:region-before {:region-name "xsl-region-before" :extent (make-inch fp-top-margin)}]
     [:fo:region-after {:region-name "xsl-region-after" :extent (make-inch base-bottom-margin)}]]
    ;; second+ page
    [:fo:simple-page-master {:master-name "letter-page-two-plus"
                             :page-height "11in"   :page-width    "8.5in"
                             :margin-top  "0.2in"  :margin-bottom "0.75in"
                             :margin-left "0.3in" :margin-right  "0.3in"}
     [:fo:region-body {:margin-top    (make-inch fp-top-margin)
                       :margin-bottom (make-inch base-bottom-margin)}]
     [:fo:region-before {:region-name "xsl-region-two-plus-before" :extent (make-inch fp-top-margin)}]
     [:fo:region-after {:region-name "xsl-region-after" :extent (make-inch base-bottom-margin)}]]
    ;; layout
    [:fo:page-sequence-master {:master-name "allPages"}
     [:fo:repeatable-page-master-alternatives
      [:fo:conditional-page-master-reference {:page-position "only" :master-reference "letter-page-one"}]
      [:fo:conditional-page-master-reference {:page-position "first" :master-reference "letter-page-one"}]
      [:fo:conditional-page-master-reference {:page-position "rest" :master-reference "letter-page-two-plus"}]]]]
   [:fo:page-sequence {:id               "pages"
                       :master-reference "allPages"}
    [:fo:static-content {:flow-name "xsl-region-after"
                         :margin-left "0.1in"
                         :margin-right "0.2in"}
     (pgoa-billing-static-footer #_p-claim [])]
    [:fo:static-content {:flow-name "xsl-region-before"}
     (pgoa-billing-header :patient-address
                          {:title "Jenny Miller"
                           :street "2308 E. Hout Street"
                           :city "Mandeville" :state "LA" :zip "70560"})]
    [:fo:static-content {:flow-name "xsl-region-two-plus-before"}
     (pgoa-billing-header :patient-address
                          {:title "Jenny Miller"
                           :street "2308 E. Hout Street"
                           :city "Mandeville" :state "LA" :zip "70560"})]
    (conj
     (into [:fo:flow {:flow-name "xsl-region-body"}]
           (map (fn [p] (statement-body p)))
           (let [pages (atom [])
                 page (atom [])
                 lorem "Lorem Ipsum is simply dummy text of the printing and typesetting industry.\nLorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book.\nIt has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged.\nIt was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum."]
             (into []
                   #_(repeat 33 [":a" ":b" ":c" ":d" ":e" ":f"])
                   (comp
                    (map (fn [x]
                           (let [description (subs lorem 0 (rand #_86 (count lorem)))
                                 base-date (-> (java.util.Date.)
                                               .toInstant
                                               (LocalDateTime/ofInstant (ZoneId/of "Etc/UTC")))
                                 new-date (.. base-date
                                               toLocalDate
                                               atStartOfDay
                                               (withHour (.getHour base-date))
                                               (plusDays x)
                                               (format (DateTimeFormatter/ofPattern "yyyy-MM-dd")))]
                             [(str new-date) description ":c" ":d" ":e" ":f"])))
                    (map (fn [[d desc c p-a b-p b-i]]
                           (let [ls (clojure.string/split desc #"\n")]
                             (into [[d (first ls) c p-a b-p b-i]]
                                   (map (fn [l] [[:fo:leader] l [:fo:leader]
                                                 [:fo:leader] [:fo:leader]
                                                 [:fo:leader]]))
                                   (rest ls)))))
                    (map (fn [lines]
                           (mapcat (fn [[d desc c p-a b-p b-i]]
                                     (let [ls (map (partial apply str) (partition-all 86 desc))]
                                       (into [[d (first ls) c p-a b-p b-i]]
                                             (map (fn [l] [[:fo:leader] l [:fo:leader]
                                                           [:fo:leader] [:fo:leader]
                                                           [:fo:leader]]))
                                             (rest ls))))
                                   lines)))
                    (map (fn [lines]
                           (let [p-c (count @page)]
                             (if (<= (+ p-c (count lines)) 33)
                               (swap! page into lines)
                               (do
                                 (swap! pages conj @page)
                                 (reset! page (into [] lines))))))))
                   (range 33))
             @pages))
      [:fo:block {:id "terminator"}])]])

(defn make-pdf []
  
  (let [test-file "borland.pdf"
        base-dir  (File. ".")
        out-dir   (File. base-dir "out")
        _         (.mkdirs out-dir)
        pdf-file  (File. out-dir test-file)
        fops      (FileOutputStream. pdf-file)
        serialized-doc (-> pgoa-bill-layout
                           (->dom :document-namespace fo-ns)
                           ;; serialize-dom
                           ;(->> (str "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
                           )]
    ;; pgoa-bill-layout
    (convert-dom->pdf serialized-doc fops)
    )
  )
