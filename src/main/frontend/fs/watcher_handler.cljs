(ns frontend.fs.watcher-handler
  (:require [clojure.string :as string]
            [frontend.config :as config]
            [frontend.db :as db]
            [frontend.db.model :as model]
            [frontend.encrypt :as encrypt]
            [frontend.handler.editor :as editor]
            [frontend.handler.extract :as extract]
            [frontend.handler.file :as file-handler]
            [frontend.handler.page :as page-handler]
            [frontend.handler.repo :as repo-handler]
            [frontend.handler.ui :as ui-handler]
            [frontend.util :as util]
            [lambdaisland.glogi :as log]
            [electron.ipc :as ipc]
            [promesa.core :as p]
            [frontend.state :as state]))

;; all IPC paths must be normalized! (via util/path-normalize)

(defn- set-missing-block-ids!
  [content]
  (when (string? content)
    (doseq [block-id (extract/extract-all-block-refs content)]
      (when-let [block (try
                         (model/get-block-by-uuid block-id)
                         (catch js/Error _e
                           nil))]
        (let [id-property (:id (:block/properties block))]
          (when-not (= (str id-property) (str block-id))
            (editor/set-block-property! block-id "id" block-id)))))))

(defn- handle-add-and-change!
  [repo path content db-content mtime backup?]
  (p/let [
          ;; save the previous content in a versioned bak file to avoid data overwritten.
          _ (when backup? (ipc/ipc "backupDbFile" (config/get-local-dir repo) path db-content content))
          _ (file-handler/alter-file repo path content {:re-render-root? true
                                                        :from-disk? true})]
    (set-missing-block-ids! content)
    (db/set-file-last-modified-at! repo path mtime)))

(defn handle-changed!
  [type {:keys [dir path content stat] :as payload}]
  (when dir
    (let [path (util/path-normalize path)
          repo (config/get-local-repo dir)
          pages-metadata-path (config/get-pages-metadata-path)
          {:keys [mtime]} stat
          db-content (or (db/get-file repo path) "")]
      (when (and (or content (= type "unlink"))
                 (not (encrypt/content-encrypted? content))
                 (not (:encryption/graph-parsing? @state/state)))
        (cond
          (and (= "add" type)
               (not= (string/trim content) (string/trim db-content))
               (not= path pages-metadata-path))
          (let [backup? (not (string/blank? db-content))]
            (handle-add-and-change! repo path content db-content mtime backup?))

          (and (= "change" type)
               (not (db/file-exists? repo path)))
          (js/console.error "Can't get file in the db: " path)

          (and (= "change" type)
               (not= (string/trim content) (string/trim db-content))
               (not= path pages-metadata-path))
          (when-not (and
                     (string/includes? path (str "/" (config/get-journals-directory) "/"))
                     (or
                      (= (string/trim content)
                         (string/trim (or (state/get-default-journal-template) "")))
                      (= (string/trim content) "-")
                      (= (string/trim content) "*")))
            (handle-add-and-change! repo path content db-content mtime true))

          (and (= "unlink" type)
               (db/file-exists? repo path))
          (when-let [page-name (db/get-file-page path)]
            (println "Delete page: " page-name ", file path: " path ".")
            (page-handler/delete! page-name #() :delete-file? false))

          (and (contains? #{"add" "change" "unlink"} type)
               (string/ends-with? path "logseq/custom.css"))
          (do
            (println "reloading custom.css")
            (ui-handler/add-style-if-exists!))

          ;; When metadata is added to watcher, update timestamps in db accordingly
          ;; This event is not triggered on re-index
          ;; Persistent metadata is gold standard when db is offline, so it's forced
          (and (contains? #{"add"} type)
               (= path pages-metadata-path))
          (p/do! (repo-handler/update-pages-metadata! repo content true))

          ;; Change is triggered by external changes, so update to the db
          ;; Don't forced update when db is online, but resolving conflicts
          (and (contains? #{"change"} type)
               (= path pages-metadata-path))
          (p/do! (repo-handler/update-pages-metadata! repo content false))

          (contains? #{"add" "change" "unlink"} type)
          nil

          :else
          (log/error :fs/watcher-no-handler {:type type
                                             :payload payload})))

      ;; return nil, otherwise the entire db will be transfered by ipc
      nil)))
