(ns frontend.util
  #?(:clj (:refer-clojure :exclude [format]))
  #?(:cljs (:require-macros [frontend.util]))
  #?(:cljs (:require
            ["/frontend/selection" :as selection]
            ["/frontend/utils" :as utils]
            ["@capacitor/status-bar" :refer [^js StatusBar Style]]
            ["grapheme-splitter" :as GraphemeSplitter]
            ["remove-accents" :as removeAccents]
            [camel-snake-kebab.core :as csk]
            [camel-snake-kebab.extras :as cske]
            [cljs-bean.core :as bean]
            [cljs-time.coerce :as tc]
            [cljs-time.core :as t]
            [dommy.core :as d]
            [frontend.mobile.util :refer [is-native-platform?]]
            [goog.dom :as gdom]
            [goog.object :as gobj]
            [goog.string :as gstring]
            [goog.userAgent]
            [promesa.core :as p]
            [rum.core :as rum]))
  (:require
   [clojure.core.async :as async]
   [clojure.pprint]
   [clojure.string :as string]
   [clojure.walk :as walk]))

#?(:cljs (goog-define NODETEST false)
   :clj (def NODETEST false))
(defonce node-test? NODETEST)

#?(:cljs
   (extend-protocol IPrintWithWriter
     js/Symbol
     (-pr-writer [sym writer _]
       (-write writer (str "\"" (.toString sym) "\"")))))

#?(:cljs (defonce ^js node-path utils/nodePath))
#?(:cljs (defn app-scroll-container-node []
           (gdom/getElement "main-content-container")))

#?(:cljs
   (defn ios?
     []
     (utils/ios)))

#?(:cljs
   (defn safari?
     []
     (let [ua (string/lower-case js/navigator.userAgent)]
       (and (string/includes? ua "webkit")
            (not (string/includes? ua "chrome"))))))

(defn safe-re-find
  [pattern s]
  #?(:cljs
     (when-not (string? s)
       ;; TODO: sentry
       (js/console.trace)))
  (when (string? s)
    (re-find pattern s)))

#?(:cljs
   (defn mobile?
     []
     (when-not node-test?
       (safe-re-find #"Mobi" js/navigator.userAgent))))

#?(:cljs
   (defn electron?
     []
     (when (and js/window (gobj/get js/window "navigator"))
       (let [ua (string/lower-case js/navigator.userAgent)]
         (string/includes? ua " electron")))))

#?(:cljs
   (defn mocked-open-dir-path
     "Mocked open DIR path for by-passing open dir in electron during testing. Nil if not given"
     []
     (when (electron?) (. js/window -__MOCKED_OPEN_DIR_PATH__))))

#?(:cljs
   (def nfs? (and (not (electron?))
                  (not (is-native-platform?)))))

#?(:cljs
   (defn file-protocol?
     []
     (string/starts-with? js/window.location.href "file://")))

(defn format
  [fmt & args]
  #?(:cljs (apply gstring/format fmt args)
     :clj (apply clojure.core/format fmt args)))

#?(:cljs
   (defn evalue
     [event]
     (gobj/getValueByKeys event "target" "value")))

#?(:cljs
   (defn ekey [event]
     (gobj/getValueByKeys event "key")))

#?(:cljs
   (defn echecked? [event]
     (gobj/getValueByKeys event "target" "checked")))

#?(:cljs
   (defn set-change-value
     "compatible change event for React"
     [node value]
     (utils/triggerInputChange node value)))

#?(:cljs
   (defn p-handle
     ([p ok-handler]
      (p-handle p ok-handler (fn [error]
                               (js/console.error error))))
     ([p ok-handler error-handler]
      (-> p
          (p/then (fn [result]
                    (ok-handler result)))
          (p/catch (fn [error]
                     (error-handler error)))))))

#?(:cljs
   (defn get-width
     []
     (gobj/get js/window "innerWidth")))

#?(:cljs
   (defn set-theme-light
     []
     (p/do!
      (.setStyle StatusBar (clj->js {:style (.-Light Style)})))))

#?(:cljs
   (defn set-theme-dark
     []
     (p/do!
      (.setStyle StatusBar (clj->js {:style (.-Dark Style)})))))

(defn find-first
  [pred coll]
  (first (filter pred coll)))

;; (defn format
;;   [fmt & args]
;;   (apply gstring/format fmt args))

#?(:cljs
   (defn json->clj
     ([json-string]
      (json->clj json-string false))
     ([json-string kebab?]
      (let [m (-> json-string
                  (js/JSON.parse)
                  (js->clj :keywordize-keys true))]
        (if kebab?
          (cske/transform-keys csk/->kebab-case-keyword m)
          m)))))

(defn remove-nils
  "remove pairs of key-value that has nil value from a (possibly nested) map."
  [nm]
  (walk/postwalk
   (fn [el]
     (if (map? el)
       (into {} (remove (comp nil? second)) el)
       el))
   nm))

(defn remove-nils-non-nested
  [nm]
  (into {} (remove (comp nil? second)) nm))

(defn ext-of-image? [s]
  (some #(-> (string/lower-case s)
             (string/ends-with? %))
        [".png" ".jpg" ".jpeg" ".bmp" ".gif" ".webp" ".svg"]))

;; ".lg:absolute.lg:inset-y-0.lg:right-0.lg:w-1/2"
(defn hiccup->class
  [class]
  (some->> (string/split class #"\.")
           (string/join " ")
           (string/trim)))

#?(:cljs
   (defn fetch-raw
     ([url on-ok on-failed]
      (fetch-raw url {} on-ok on-failed))
     ([url opts on-ok on-failed]
      (-> (js/fetch url (bean/->js opts))
          (.then (fn [resp]
                   (if (>= (.-status resp) 400)
                     (on-failed resp)
                     (if (.-ok resp)
                       (-> (.text resp)
                           (.then bean/->clj)
                           (.then #(on-ok %)))
                       (on-failed resp)))))))))

#?(:cljs
   (defn fetch
     ([url on-ok on-failed]
      (fetch url {} on-ok on-failed))
     ([url opts on-ok on-failed]
      (-> (js/fetch url (bean/->js opts))
          (.then (fn [resp]
                   (if (>= (.-status resp) 400)
                     (on-failed resp)
                     (if (.-ok resp)
                       (-> (.json resp)
                           (.then bean/->clj)
                           (.then #(on-ok %)))
                       (on-failed resp)))))))))

#?(:cljs
   (defn upload
     [url file on-ok on-failed on-progress]
     (let [xhr (js/XMLHttpRequest.)]
       (.open xhr "put" url)
       (gobj/set xhr "onload" on-ok)
       (gobj/set xhr "onerror" on-failed)
       (when (and (gobj/get xhr "upload")
                  on-progress)
         (gobj/set (gobj/get xhr "upload")
                   "onprogress"
                   on-progress))
       (.send xhr file))))

#?(:cljs
   (defn post
     [url body on-ok on-failed]
     (fetch url {:method "post"
                 :headers {:Content-Type "application/json"}
                 :body (js/JSON.stringify (clj->js body))}
            on-ok
            on-failed)))

#?(:cljs
   (defn delete
     [url on-ok on-failed]
     (fetch url {:method "delete"
                 :headers {:Content-Type "application/json"}}
            on-ok
            on-failed)))

(defn zero-pad
  [n]
  (if (< n 10)
    (str "0" n)
    (str n)))

(defn parse-int
  [x]
  #?(:cljs (if (string? x)
             (js/parseInt x)
             x)
     :clj (if (string? x)
            (Integer/parseInt x)
            x)))

(defn safe-parse-int
  [x]
  #?(:cljs (let [result (parse-int x)]
             (if (js/isNaN result)
               nil
               result))
     :clj ((try
             (parse-int x)
             (catch Exception _
               nil)))))
#?(:cljs
   (defn parse-float
     [x]
     (if (string? x)
       (js/parseFloat x)
       x)))

#?(:cljs
   (defn safe-parse-float
     [x]
     (let [result (parse-float x)]
       (if (js/isNaN result)
         nil
         result))))

#?(:cljs
   (defn debounce
     "Returns a function that will call f only after threshold has passed without new calls
      to the function. Calls prep-fn on the args in a sync way, which can be used for things like
      calling .persist on the event object to be able to access the event attributes in f"
     ([threshold f] (debounce threshold f (constantly nil)))
     ([threshold f prep-fn]
      (let [t (atom nil)]
        (fn [& args]
          (when @t (js/clearTimeout @t))
          (apply prep-fn args)
          (reset! t (js/setTimeout #(do
                                      (reset! t nil)
                                      (apply f args))
                                   threshold)))))))

(defn nth-safe [c i]
  (if (or (< i 0) (>= i (count c)))
    nil
    (nth c i)))

#?(:cljs
   (when-not node-test?
     (extend-type js/NodeList
       ISeqable
       (-seq [array] (array-seq array 0)))))

;; Caret
#?(:cljs
   (defn caret-range [node]
     (when-let [doc (or (gobj/get node "ownerDocument")
                        (gobj/get node "document"))]
       (let [win (or (gobj/get doc "defaultView")
                     (gobj/get doc "parentWindow"))
             selection (.getSelection win)]
         (if selection
           (let [range-count (gobj/get selection "rangeCount")]
             (when (> range-count 0)
               (let [range (-> (.getSelection win)
                               (.getRangeAt 0))
                     pre-caret-range (.cloneRange range)]
                 (.selectNodeContents pre-caret-range node)
                 (.setEnd pre-caret-range
                          (gobj/get range "endContainer")
                          (gobj/get range "endOffset"))
                 (let [contents (.cloneContents pre-caret-range)
                       html (some-> (first (.-childNodes contents))
                                    (gobj/get "innerHTML")
                                    str)
                       ;; FIXME: this depends on the dom structure,
                       ;; need a converter from html to text includes newlines
                       br-ended? (and html
                                      (or
                                       ;; first line with a new line
                                       (string/ends-with? html "<div class=\"is-paragraph\"></div></div></span></div></div></div>")
                                       ;; multiple lines with a new line
                                       (string/ends-with? html "<br></div></div></span></div></div></div>")))
                       value (.toString pre-caret-range)]
                   (if br-ended?
                     (str value "\n")
                     value)))))
           (when-let [selection (gobj/get doc "selection")]
             (when (not= "Control" (gobj/get selection "type"))
               (let [text-range (.createRange selection)
                     pre-caret-text-range (.createTextRange (gobj/get doc "body"))]
                 (.moveToElementText pre-caret-text-range node)
                 (.setEndPoint pre-caret-text-range "EndToEnd" text-range)
                 (gobj/get pre-caret-text-range "text")))))))))

(defn get-selection-start
  [input]
  (when input
    (.-selectionStart input)))

(defn get-selection-end
  [input]
  (when input
    (.-selectionEnd input)))

(defn get-first-or-last-line-pos
  [input]
  (let [pos (get-selection-start input)
        value (.-value input)
        last-newline-pos (or (string/last-index-of value \newline (dec pos)) -1)]
    (- pos last-newline-pos 1)))

#?(:cljs
   (defn stop [e]
     (when e (doto e (.preventDefault) (.stopPropagation)))))

#?(:cljs
   (defn stop-propagation [e]
     (when e (.stopPropagation e))))

#?(:cljs
   (defn cur-doc-top []
     (.. js/document -documentElement -scrollTop)))

#?(:cljs
   (defn element-top [elem top]
     (when elem
       (if (.-offsetParent elem)
         (let [client-top (or (.-clientTop elem) 0)
               offset-top (.-offsetTop elem)]
           (+ top client-top offset-top (element-top (.-offsetParent elem) top)))
         top))))

#?(:cljs
   (defn scroll-to-element
     [elem-id]
     (when-not (safe-re-find #"^/\d+$" elem-id)
       (when elem-id
         (when-let [elem (gdom/getElement elem-id)]
           (.scroll (app-scroll-container-node)
                    #js {:top (let [top (element-top elem 0)]
                                (if (< top 256)
                                  0
                                  (- top 80)))
                         :behavior "smooth"}))))))

#?(:cljs
   (defn scroll-to
     ([pos]
      (scroll-to (app-scroll-container-node) pos))
     ([node pos]
      (scroll-to node pos true))
     ([node pos animate?]
      (when node
        (.scroll node
                 #js {:top      pos
                      :behavior (if animate? "smooth" "auto")})))))

#?(:cljs
   (defn scroll-top
     "Returns the scroll top position of the `node`. If `node` is not specified,
     returns the scroll top position of the `app-scroll-container-node`."
     ([]
      (scroll-top (app-scroll-container-node)))
     ([node]
      (when node (.-scrollTop node)))))

#?(:cljs
   (defn scroll-to-top
     ([]
      (scroll-to (app-scroll-container-node) 0 false))
     ([animate?]
      (scroll-to (app-scroll-container-node) 0 animate?))))

#?(:cljs
   (defn url-encode
     [string]
     (some-> string str (js/encodeURIComponent) (.replace "+" "%20"))))

#?(:cljs
   (defn url-decode
     [string]
     (some-> string str (js/decodeURIComponent))))

#?(:cljs
   (defn link?
     [node]
     (contains?
      #{"A" "BUTTON"}
      (gobj/get node "tagName"))))

#?(:cljs
   (defn time?
     [node]
     (contains?
      #{"TIME"}
      (gobj/get node "tagName"))))

#?(:cljs
   (defn audio?
     [node]
     (contains?
      #{"AUDIO"}
      (gobj/get node "tagName"))))

#?(:cljs
   (defn video?
     [node]
     (contains?
      #{"VIDEO"}
      (gobj/get node "tagName"))))

#?(:cljs
   (defn sup?
     [node]
     (contains?
      #{"SUP"}
      (gobj/get node "tagName"))))

#?(:cljs
   (defn input?
     [node]
     (when node
       (contains?
        #{"INPUT" "TEXTAREA"}
        (gobj/get node "tagName")))))

#?(:cljs
   (defn select?
     [node]
     (when node
       (= "SELECT" (gobj/get node "tagName")))))

#?(:cljs
   (defn details-or-summary?
     [node]
     (when node
       (contains?
        #{"DETAILS" "SUMMARY"}
        (gobj/get node "tagName")))))

;; Debug
(defn starts-with?
  [s substr]
  (string/starts-with? s substr))

(defn distinct-by
  [f col]
  (reduce
   (fn [acc x]
     (if (some #(= (f x) (f %)) acc)
       acc
       (vec (conj acc x))))
   []
   col))

(defn distinct-by-last-wins
  [f col]
  (reduce
   (fn [acc x]
     (if (some #(= (f x) (f %)) acc)
       (mapv
        (fn [v]
          (if (= (f x) (f v))
            x
            v))
        acc)
       (vec (conj acc x))))
   []
   col))

(defn get-git-owner-and-repo
  [repo-url]
  (take-last 2 (string/split repo-url #"/")))

(defn safe-lower-case
  [s]
  (if (string? s)
    (string/lower-case s) s))

(defn split-first [pattern s]
  (when-let [first-index (string/index-of s pattern)]
    [(subs s 0 first-index)
     (subs s (+ first-index (count pattern)) (count s))]))

(defn split-last [pattern s]
  (when-let [last-index (string/last-index-of s pattern)]
    [(subs s 0 last-index)
     (subs s (+ last-index (count pattern)) (count s))]))

(defn trim-safe
  [s]
  (when s
    (string/trim s)))

(defn trimr-without-newlines
  [s]
  (.replace s #"[ \t\r]+$" ""))

(defn triml-without-newlines
  [s]
  (.replace s #"^[ \t\r]+" ""))

(defn concat-without-spaces
  [left right]
  (when (and (string? left)
             (string? right))
    (let [left (trimr-without-newlines left)
          not-space? (or
                      (string/blank? left)
                      (= "\n" (last left)))]
      (str left
           (when-not not-space? " ")
           (triml-without-newlines right)))))

;; Add documentation
(defn replace-first [pattern s new-value]
  (if-let [first-index (string/index-of s pattern)]
    (str new-value (subs s (+ first-index (count pattern))))
    s))

(defn replace-last
  ([pattern s new-value]
   (replace-last pattern s new-value true))
  ([pattern s new-value space?]
   (if-let [last-index (string/last-index-of s pattern)]
     (let [prefix (subs s 0 last-index)]
       (if space?
         (concat-without-spaces prefix new-value)
         (str prefix new-value)))
     s)))

(defonce default-escape-chars "[]{}().+*?|")

(defn replace-ignore-case
  [s old-value new-value & [escape-chars]]
  (let [escape-chars (or escape-chars default-escape-chars)
        old-value (if (string? escape-chars)
                    (reduce (fn [acc escape-char]
                              (string/replace acc escape-char (str "\\" escape-char)))
                            old-value escape-chars)
                    old-value)]
    (string/replace s (re-pattern (str "(?i)" old-value)) new-value)))

;; copy from https://stackoverflow.com/questions/18735665/how-can-i-get-the-positions-of-regex-matches-in-clojurescript
#?(:cljs
   (defn re-pos [re s]
     (let [re (js/RegExp. (.-source re) "g")]
       (loop [res []]
         (if-let [m (.exec re s)]
           (recur (conj res [(.-index m) (first m)]))
           res)))))

#?(:cljs
   (defn safe-set-range-text!
     ([input text start end]
      (try
        (.setRangeText input text start end)
        (catch js/Error _e
          nil)))
     ([input text start end select-mode]
      (try
        (.setRangeText input text start end select-mode)
        (catch js/Error _e
          nil)))))

#?(:cljs
   ;; for widen char
   (defn safe-dec-current-pos-from-end
     [input current-pos]
     (if-let [len (and (string? input) (.-length input))]
       (when-let [input (and (>= len 2) (<= current-pos len)
                             (.substring input (max (- current-pos 20) 0) current-pos))]
         (try
           (let [^js splitter (GraphemeSplitter.)
                 ^js input (.splitGraphemes splitter input)]
             (- current-pos (.-length (.pop input))))
           (catch js/Error e
             (js/console.error e)
             (dec current-pos))))
       (dec current-pos))))

#?(:cljs
   ;; for widen char
   (defn safe-inc-current-pos-from-start
     [input current-pos]
     (if-let [len (and (string? input) (.-length input))]
       (when-let [input (and (>= len 2) (<= current-pos len)
                             (.substr input current-pos 20))]
         (try
           (let [^js splitter (GraphemeSplitter.)
                 ^js input (.splitGraphemes splitter input)]
             (+ current-pos (.-length (.shift input))))
           (catch js/Error e
             (js/console.error e)
             (inc current-pos))))
       (inc current-pos))))

#?(:cljs
   (defn kill-line-before!
     [input]
     (let [val (.-value input)
           end (get-selection-start input)
           n-pos (string/last-index-of val \newline (dec end))
           start (if n-pos (inc n-pos) 0)]
       (safe-set-range-text! input "" start end))))

#?(:cljs
   (defn kill-line-after!
     [input]
     (let [val   (.-value input)
           start (get-selection-start input)
           end   (or (string/index-of val \newline start)
                     (count val))]
       (safe-set-range-text! input "" start end))))

#?(:cljs
   (defn insert-at-current-position!
     [input text]
     (let [start (get-selection-start input)
           end   (get-selection-end input)]
       (safe-set-range-text! input text start end "end"))))

;; copied from re_com
#?(:cljs
   (defn deref-or-value
     "Takes a value or an atom
      If it's a value, returns it
      If it's a Reagent object that supports IDeref, returns the value inside it by derefing
      "
     [val-or-atom]
     (if (satisfies? IDeref val-or-atom)
       @val-or-atom
       val-or-atom)))

;; copied from re_com
#?(:cljs
   (defn now->utc
     "Return a goog.date.UtcDateTime based on local date/time."
     []
     (let [local-date-time (js/goog.date.DateTime.)]
       (js/goog.date.UtcDateTime.
        (.getYear local-date-time)
        (.getMonth local-date-time)
        (.getDate local-date-time)
        0 0 0 0))))

(defn safe-subvec [xs start end]
  (if (or (neg? start)
          (> end (count xs)))
    []
    (subvec xs start end)))

(defn safe-subs
  ([s start]
   (let [c (count s)]
     (safe-subs s start c)))
  ([s start end]
   (let [c (count s)]
     (subs s (min c start) (min c end)))))

#?(:cljs
   (defn get-nodes-between-two-nodes
     [id1 id2 class]
     (when-let [nodes (array-seq (js/document.getElementsByClassName class))]
       (let [node-1 (gdom/getElement id1)
             node-2 (gdom/getElement id2)
             idx-1 (.indexOf nodes node-1)
             idx-2 (.indexOf nodes node-2)
             start (min idx-1 idx-2)
             end (inc (max idx-1 idx-2))]
         (safe-subvec (vec nodes) start end)))))

#?(:cljs
   (defn get-direction-between-two-nodes
     [id1 id2 class]
     (when-let [nodes (array-seq (js/document.getElementsByClassName class))]
       (let [node-1 (gdom/getElement id1)
             node-2 (gdom/getElement id2)
             idx-1 (.indexOf nodes node-1)
             idx-2 (.indexOf nodes node-2)]
         (if (>= idx-1 idx-2)
           :up
           :down)))))

#?(:cljs
   (defn rec-get-blocks-container
     [node]
     (if (and node (d/has-class? node "blocks-container"))
       node
       (and node
            (rec-get-blocks-container (gobj/get node "parentNode"))))))

#?(:cljs
   (defn rec-get-blocks-content-section
     [node]
     (if (and node (d/has-class? node "content"))
       node
       (and node
            (rec-get-blocks-content-section (gobj/get node "parentNode"))))))

#?(:cljs
   (defn get-blocks-noncollapse []
     (->> (d/by-class "ls-block")
          (filter (fn [b] (some? (gobj/get b "offsetParent")))))))

#?(:cljs
   (defn remove-embeded-blocks [blocks]
     (->> blocks
          (remove (fn [b] (= "true" (d/attr b "data-embed")))))))

#?(:cljs
   (defn get-selected-text
     []
     (utils/getSelectionText)))

#?(:cljs (def clear-selection! selection/clearSelection))

#?(:cljs
   (defn copy-to-clipboard!
     ([s]
      (utils/writeClipboard s false))
     ([s html?]
      (utils/writeClipboard s html?))))

(def uuid-pattern "[0-9a-f]{8}-[0-9a-f]{4}-[0-5][0-9a-f]{3}-[089ab][0-9a-f]{3}-[0-9a-f]{12}")
(defonce exactly-uuid-pattern (re-pattern (str "(?i)^" uuid-pattern "$")))
(defn uuid-string?
  [s]
  (safe-re-find exactly-uuid-pattern s))

(defn drop-nth [n coll]
  (keep-indexed #(when (not= %1 n) %2) coll))

(defn capitalize-all [s]
  (some->> (string/split s #" ")
           (map string/capitalize)
           (string/join " ")))

#?(:cljs
   (defn react
     [ref]
     (if rum.core/*reactions*
       (rum/react ref)
       @ref)))

(defn time-ms
  []
  #?(:cljs (tc/to-long (cljs-time.core/now))))

;; Returns the milliseconds representation of the provided time, in the local timezone.
;; For example, if you run this function at 10pm EDT in the EDT timezone on May 31st,
;; it will return 1622433600000, which is equivalent to Mon May 31 2021 00 :00:00.
#?(:cljs
   (defn today-at-local-ms [hours mins secs millisecs]
     (.setHours (js/Date. (.now js/Date)) hours mins secs millisecs)))

(defn d
  [k f]
  (let [result (atom nil)]
    (println (str "Debug " k))
    (time (reset! result (doall (f))))
    @result))

(defn concat-without-nil
  [& cols]
  (->> (apply concat cols)
       (remove nil?)))

#?(:cljs
   (defn set-title!
     [title]
     (set! (.-title js/document) title)))

#?(:cljs
   (defn get-block-container
     [block-element]
     (when block-element
       (when-let [section (some-> (rec-get-blocks-content-section block-element)
                                  (d/parent))]
         (when section
           (gdom/getElement section "id"))))))

#?(:cljs
   (defn get-prev-block-non-collapsed
     [block]
     (when-let [blocks (get-blocks-noncollapse)]
       (when-let [index (.indexOf blocks block)]
         (let [idx (dec index)]
           (when (>= idx 0)
             (nth-safe blocks idx)))))))

#?(:cljs
   (defn get-prev-block-non-collapsed-non-embed
     [block]
     (when-let [blocks (->> (get-blocks-noncollapse)
                            remove-embeded-blocks)]
       (when-let [index (.indexOf blocks block)]
         (let [idx (dec index)]
           (when (>= idx 0)
             (nth-safe blocks idx)))))))

#?(:cljs
   (defn get-next-block-non-collapsed
     [block]
     (when-let [blocks (get-blocks-noncollapse)]
       (when-let [index (.indexOf blocks block)]
         (let [idx (inc index)]
           (when (>= (count blocks) idx)
             (nth-safe blocks idx)))))))

#?(:cljs
   (defn get-next-block-non-collapsed-skip
     [block]
     (when-let [blocks (get-blocks-noncollapse)]
       (when-let [index (.indexOf blocks block)]
         (loop [idx (inc index)]
           (when (>= (count blocks) idx)
             (let [block (nth-safe blocks idx)
                   nested? (->> (array-seq (gdom/getElementsByClass "selected"))
                                (some (fn [dom] (.contains dom block))))]
               (if nested?
                 (recur (inc idx))
                 block))))))))

(defn rand-str
  [n]
  #?(:cljs (-> (.toString (js/Math.random) 36)
               (.substr 2 n))
     :clj (->> (repeatedly #(Integer/toString (rand 36) 36))
               (take n)
               (apply str))))

(defn unique-id
  []
  (str (rand-str 6) (rand-str 3)))

(defn tag-valid?
  [tag-name]
  (when (string? tag-name)
    (not (safe-re-find #"[# \t\r\n]+" tag-name))))

(defn pp-str [x]
  #_:clj-kondo/ignore
  (with-out-str (clojure.pprint/pprint x)))

(defn hiccup-keywordize
  [hiccup]
  (walk/postwalk
   (fn [f]
     (if (and (vector? f) (string? (first f)))
       (update f 0 keyword)
       f))
   hiccup))

#?(:cljs
   (defn chrome?
     []
     (let [user-agent js/navigator.userAgent
           vendor js/navigator.vendor]
       (and (safe-re-find #"Chrome" user-agent)
            (safe-re-find #"Google Inc" vendor)))))

#?(:cljs
   (defn indexeddb-check?
     [error-handler]
     (let [test-db "logseq-test-db-foo-bar-baz"
           db (and js/window.indexedDB
                   (js/window.indexedDB.open test-db))]
       (when (and db (not (chrome?)))
         (gobj/set db "onerror" error-handler)
         (gobj/set db "onsuccess"
                   (fn []
                     (js/window.indexedDB.deleteDatabase test-db)))))))

(defonce mac? #?(:cljs goog.userAgent/MAC
                 :clj nil))

(defonce win32? #?(:cljs goog.userAgent/WINDOWS
                   :clj nil))

#?(:cljs
   (defn absolute-path?
     [path]
     (try
       (js/window.apis.isAbsolutePath path)
       (catch js/Error _
         (utils/win32 path)))))

(defn default-content-with-title
  [text-format]
  (case (name text-format)
    "org"
    "* "

    "- "))

#?(:cljs
   (defn get-first-block-by-id
     [block-id]
     (when block-id
       (let [block-id (str block-id)]
         (when (uuid-string? block-id)
           (first (array-seq (js/document.getElementsByClassName block-id))))))))

(def windows-reserved-chars #"[:\\*\\?\"<>|]+")

(defn include-windows-reserved-chars?
  [s]
  (safe-re-find windows-reserved-chars s))

(defn create-title-property?
  [s]
  (and (string? s)
       (or (include-windows-reserved-chars? s)
           (string/includes? s "_")
           (string/includes? s "/")
           (string/includes? s ".")
           (string/includes? s "%")
           (string/includes? s "#"))))

(defn remove-boundary-slashes
  [s]
  (when (string? s)
    (let [s (if (= \/ (first s))
              (subs s 1)
              s)]
      (if (= \/ (last s))
        (subs s 0 (dec (count s)))
        s))))

(defn normalize
  [s]
  (.normalize s "NFC"))
(defn path-normalize
  "Normalize file path (for reading paths from FS, not required by writting)"
  [s]
  (.normalize s "NFC"))

#?(:cljs
   (defn search-normalize
     "Normalize string for searching (loose)"
     [s]
     (removeAccents (.normalize (string/lower-case s) "NFKC"))))

(defn page-name-sanity
  "Sanitize the page-name for file name (strict), for file writting"
  ([page-name]
   (page-name-sanity page-name false))
  ([page-name replace-slash?]
   (let [page (some-> page-name
                      (remove-boundary-slashes)
                      ;; Windows reserved path characters
                      (string/replace windows-reserved-chars "_")
                      ;; for android filesystem compatiblity
                      (string/replace #"[\\#|%]+" "_")
                      (normalize))]
     (if replace-slash?
       (string/replace page #"/" ".")
       page))))

(defn page-name-sanity-lc
  "Sanitize the query string for a page name (mandate for :block/name)"
  [s]
  (page-name-sanity (string/lower-case s)))

(defn safe-page-name-sanity-lc
  [s]
  (if (string? s)
    (page-name-sanity-lc s) s))

(defn get-page-original-name
  [page]
  (or (:block/original-name page)
      (:block/name page)))

#?(:cljs
   (defn add-style!
     [style]
     (when (some? style)
       (let [parent-node (d/sel1 :head)
             id "logseq-custom-theme-id"
             old-link-element (d/sel1 (str "#" id))
             style (if (string/starts-with? style "http")
                     style
                     (str "data:text/css;charset=utf-8," (js/encodeURIComponent style)))]
         (when old-link-element
           (d/remove! old-link-element))
         (let [link (->
                     (d/create-element :link)
                     (d/set-attr! :id id)
                     (d/set-attr! :rel "stylesheet")
                     (d/set-attr! :type "text/css")
                     (d/set-attr! :href style)
                     (d/set-attr! :media "all"))]
           (d/append! parent-node link))))))

(defn ->platform-shortcut
  [keyboard-shortcut]
  (let [result (or keyboard-shortcut "")
        result (string/replace result "left" "←")
        result (string/replace result "right" "→")]
    (if mac?
      (-> result
          (string/replace "Ctrl" "Cmd")
          (string/replace "Alt" "Opt"))
      result)))

(defn remove-common-preceding
  [col1 col2]
  (if (and (= (first col1) (first col2))
           (seq col1))
    (recur (rest col1) (rest col2))
    [col1 col2]))

;; fs
(defn get-file-ext
  [file]
  (and
   (string? file)
   (string/includes? file ".")
   (some-> (last (string/split file #"\.")) string/lower-case)))

(defn get-dir-and-basename
  [path]
  (let [parts (string/split path "/")
        basename (last parts)
        dir (->> (butlast parts)
                 (string/join "/"))]
    [dir basename]))

(defn get-relative-path
  [current-file-path another-file-path]
  (let [directories-f #(butlast (string/split % "/"))
        parts-1 (directories-f current-file-path)
        parts-2 (directories-f another-file-path)
        [parts-1 parts-2] (remove-common-preceding parts-1 parts-2)
        another-file-name (last (string/split another-file-path "/"))]
    (->> (concat
          (if (seq parts-1)
            (repeat (count parts-1) "..")
            ["."])
          parts-2
          [another-file-name])
         (string/join "/"))))

;; Copied from https://github.com/tonsky/datascript-todo
#?(:clj
   (defmacro profile
     [k & body]
     `(if goog.DEBUG
        (let [k# ~k]
          (.time js/console k#)
          (let [res# (do ~@body)]
            (.timeEnd js/console k#)
            res#))
        (do ~@body))))

;; TODO: profile and profileEnd

;; Copy from hiccup
(defn escape-html
  "Change special characters into HTML character entities."
  [text]
  (-> text
      (string/replace "&"  "&amp;")
      (string/replace "<"  "&lt;")
      (string/replace ">"  "&gt;")
      (string/replace "\"" "&quot;")
      (string/replace "'" "&apos;")))

(defn unescape-html
  [text]
  (-> text
      (string/replace "&amp;" "&")
      (string/replace "&lt;" "<")
      (string/replace "&gt;" ">")
      (string/replace "&quot;" "\"")
      (string/replace "&apos;" "'")))

#?(:cljs
   (defn system-locales
     []
     (when-not node-test?
       (when-let [navigator (and js/window (.-navigator js/window))]
         ;; https://zzz.buzz/2016/01/13/detect-browser-language-in-javascript/
         (when navigator
           (let [v (js->clj
                    (or
                     (.-languages navigator)
                     (.-language navigator)
                     (.-userLanguage navigator)
                     (.-browserLanguage navigator)
                     (.-systemLanguage navigator)))]
             (if (string? v) [v] v)))))))

#?(:cljs
   (defn zh-CN-supported?
     []
     (let [system-locales (set (system-locales))]
       (or (contains? system-locales "zh-CN")
           (contains? system-locales "zh-Hans-CN")))))

(comment
  (= (get-relative-path "journals/2020_11_18.org" "pages/grant_ideas.org")
     "../pages/grant_ideas.org")

  (= (get-relative-path "journals/2020_11_18.org" "journals/2020_11_19.org")
     "./2020_11_19.org")

  (= (get-relative-path "a/b/c/d/g.org" "a/b/c/e/f.org")
     "../e/f.org"))

#?(:cljs
   (defn select-highlight!
     [blocks]
     (doseq [block blocks]
       (d/add-class! block "selected noselect"))))

#?(:cljs
   (defn select-unhighlight!
     [blocks]
     (doseq [block blocks]
       (d/remove-class! block "selected" "noselect"))))

(defn keyname [key] (str (namespace key) "/" (name key)))

(defn batch [in max-time handler buf-atom]
  (async/go-loop [buf buf-atom t (async/timeout max-time)]
    (let [[v p] (async/alts! [in t])]
      (cond
        (or (= p t) (nil? v))
        (let [timeout (async/timeout max-time)]
          (handler @buf)
          (reset! buf [])
          (recur buf timeout))

        :else
        (do (swap! buf conj v)
            (recur buf t))))))

#?(:cljs
   (defn trace!
     []
     (js/console.trace)))

(defn remove-first [pred coll]
  ((fn inner [coll]
     (lazy-seq
      (when-let [[x & xs] (seq coll)]
        (if (pred x)
          xs
          (cons x (inner xs))))))
   coll))

(def pprint clojure.pprint/pprint)

#?(:cljs
   (defn backward-kill-word
     [input]
     (let [val     (.-value input)
           current (get-selection-start input)
           prev    (or
                    (->> [(string/last-index-of val \space (dec current))
                          (string/last-index-of val \newline (dec current))]
                         (remove nil?)
                         (apply max))
                    0)
           idx     (if (zero? prev)
                     0
                     (->
                      (loop [idx prev]
                        (if (#{\space \newline} (nth-safe val idx))
                          (recur (dec idx))
                          idx))
                      inc))]
       (safe-set-range-text! input "" idx current))))

#?(:cljs
   (defn forward-kill-word
     [input]
     (let [val   (.-value input)
           current (get-selection-start input)
           current (loop [idx current]
                     (if (#{\space \newline} (nth-safe val idx))
                       (recur (inc idx))
                       idx))
           idx (or (->> [(string/index-of val \space current)
                         (string/index-of val \newline current)]
                        (remove nil?)
                        (apply min))
                   (count val))]
       (safe-set-range-text! input "" current (inc idx)))))

#?(:cljs
   (defn fix-open-external-with-shift!
     [^js/MouseEvent e]
     (when (and (.-shiftKey e) win32? (electron?)
                (= (string/lower-case (.. e -target -nodeName)) "a")
                (string/starts-with? (.. e -target -href) "file:"))
       (.preventDefault e))))

(defn classnames
  "Like react classnames utility:

     ```
      [:div {:class (classnames [:a :b {:c true}])}
     ```
  "
  [args]
  (into #{} (mapcat
             #(if (map? %)
                (for [[k v] %]
                  (when v (name k)))
                [(name %)])
             args)))

#?(:cljs
   (defn- get-dom-top
     [node]
     (gobj/get (.getBoundingClientRect node) "top")))

#?(:cljs
   (defn sort-by-height
     [elements]
     (sort (fn [x y]
             (< (get-dom-top x) (get-dom-top y)))
           elements)))

#?(:cljs
   (defn calc-delta-rect-offset
     [^js/HTMLElement target ^js/HTMLElement container]
     (let [target-rect (bean/->clj (.toJSON (.getBoundingClientRect target)))
           viewport-rect {:width  (.-clientWidth container)
                          :height (.-clientHeight container)}]

       {:y (- (:height viewport-rect) (:bottom target-rect))
        :x (- (:width viewport-rect) (:right target-rect))})))

(def regex-char-esc-smap
  (let [esc-chars "{}[]()&^%$#!?*.+|\\"]
    (zipmap esc-chars
            (map #(str "\\" %) esc-chars))))

(defn regex-escape
  "Escape all regex meta chars in text."
  [text]
  (string/join (replace regex-char-esc-smap text)))

(defn split-namespace-pages
  [title]
  (let [parts (string/split title "/")]
    (loop [others (rest parts)
           result [(first parts)]]
      (if (seq others)
        (let [prev (last result)]
          (recur (rest others)
                 (conj result (str prev "/" (first others)))))
        result))))

(comment
  (re-matches (re-pattern (regex-escape "$u^8(d)+w.*[dw]d?")) "$u^8(d)+w.*[dw]d?"))

#?(:cljs
   (defn meta-key-name []
     (if mac? "Cmd" "Ctrl")))

(defn wrapped-by-quotes?
  [v]
  (and (string? v) (>= (count v) 2) (= "\"" (first v) (last v))))

(defn unquote-string
  [v]
  (string/trim (subs v 1 (dec (count v)))))

#?(:cljs
   (defn right-click?
     [e]
     (let [which (gobj/get e "which")
           button (gobj/get e "button")]
       (or (= which 3)
           (= button 2)))))

#?(:cljs
   (defn url?
     [s]
     (and (string? s)
          (try
            (js/URL. s)
            true
            (catch js/Error _e
              false)))))

#?(:cljs
   (defn make-el-into-center-viewport
     [^js/HTMLElement el]
     (when el
       (.scrollIntoView el #js {:block "center" :behavior "smooth"}))))

#?(:cljs
   (defn make-el-cursor-position-into-center-viewport
     [^js/HTMLElement el]
     (when el
       (let [main-node (gdom/getElement "main-content-container")
             pos (get-selection-start el)
             cursor-top (some-> (gdom/getElement "mock-text")
                                gdom/getChildren
                                array-seq
                                (nth-safe pos)
                                .-offsetTop)
             box-caret (.getBoundingClientRect el)
             box-top (.-top box-caret)
             box-bottom (.-bottom box-caret)
             vw-height (or (.-height js/window.visualViewport)
                           (.-clientHeight js/document.documentElement))
             scroll-top (.-scrollTop main-node)
             cursor-y (if cursor-top (+ cursor-top box-top) box-bottom)
             scroll (- cursor-y (/ vw-height 2))]
         (when (> scroll 0)
           (set! (.-scrollTop main-node) (+ scroll-top scroll)))))))

#?(:cljs
   (defn make-el-center-if-near-top
     ([^js/HTMLElement el]
      (make-el-center-if-near-top el 80))
     ([^js/HTMLElement el offset]
      (let [target-top (.-top (.getBoundingClientRect el))]
        (when (<= target-top (or (safe-parse-int offset) 0))
          (make-el-into-center-viewport el))))))

#?(:cljs
   (defn sm-breakpoint?
     []
     (< (.-offsetWidth js/document.documentElement) 640)))

#?(:cljs
   (defn event-is-composing?
     "Check if keydown event is a composing (IME) event.
      Ignore the IME process by default."
     ([e]
      (event-is-composing? e false))
     ([e include-process?]
      (let [event-composing? (gobj/getValueByKeys e "event_" "isComposing")]
        (if include-process?
          (or event-composing?
              (= (gobj/get e "keyCode") 229)
              (= (gobj/get e "key") "Process"))
          event-composing?)))))

#?(:cljs
   (defn onchange-event-is-composing?
     "Check if onchange event of Input is a composing (IME) event.
       Always ignore the IME process."
     [e]
     (gobj/getValueByKeys e "nativeEvent" "isComposing"))) ;; No keycode available

#?(:cljs
   (defn open-url
     [url]
     (let [route? (or (string/starts-with? url
                                           (string/replace js/location.href js/location.hash ""))
                      (string/starts-with? url "#"))]
       (if (and (not route?) (electron?))
         (js/window.apis.openExternal url)
         (set! (.-href js/window.location) url)))))

(defn collapsed?
  [block]
  (:block/collapsed? block))

#?(:cljs
   (defn atom? [v]
     (instance? Atom v)))

;; https://stackoverflow.com/questions/32511405/how-would-time-ago-function-implementation-look-like-in-clojure
#?(:cljs
   (defn time-ago [time]
     (let [units [{:name "second" :limit 60 :in-second 1}
                  {:name "minute" :limit 3600 :in-second 60}
                  {:name "hour" :limit 86400 :in-second 3600}
                  {:name "day" :limit 604800 :in-second 86400}
                  {:name "week" :limit 2629743 :in-second 604800}
                  {:name "month" :limit 31556926 :in-second 2629743}
                  {:name "year" :limit js/Number.MAX_SAFE_INTEGER :in-second 31556926}]
           diff (t/in-seconds (t/interval time (t/now)))]
       (if (< diff 5)
         "just now"
         (let [unit (first (drop-while #(or (>= diff (:limit %))
                                            (not (:limit %)))
                                       units))]
           (-> (/ diff (:in-second unit))
               Math/floor
               int
               (#(str % " " (:name unit) (when (> % 1) "s") " ago"))))))))
