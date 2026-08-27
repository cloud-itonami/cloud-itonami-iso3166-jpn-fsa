;; Re-fetch every entry in facts.edn from the live authority.
;;
;;   nbb scripts/verify-facts.cljs
;;
;; ── THREE EXIT CODES, ON PURPOSE
;;
;;   0  every entry checked and every entry agreed with the register
;;   1  the register is wrong about the world -- a page is gone, a law id no
;;      longer resolves, a repeal happened, a registry file dangles. A claim,
;;      from a run that was able to make claims.
;;   2  REFUSED. This run could not answer. Not a pass.
;;
;; The third code is the point, and on this host it is not academic. The
;; www.fsa.go.jp 404 page is LONGER than the front page and carries the whole
;; subject vocabulary in its navigation, so a needle that has drifted into
;; site chrome starts matching the missing page. That is a broken check, not
;; a changed page. If it reported as a failure it would be indistinguishable
;; from a real finding, and if it reported as a pass it would be worse. It
;; REFUSES.
;;
;; ── WHY EACH CHECK IS THE CHECK IT IS
;;
;; Each non-obvious decision is forced by something measured against these
;; hosts on 2026-08-27, written out in facts.edn's header rather than
;; repeated here. In short:
;;
;;   statutes are identified by total_count and law_id, NEVER by HTTP status,
;;   because the cheap listing endpoint answers a fabricated law id with 200;
;;
;;   being in force is checked as TWO fields, because repeal_status None does
;;   not mean the served revision is the current one, and remain_in_force is
;;   true on a law repealed in 2020;
;;
;;   every page needle is re-subtracted from the LIVE 404 body each run, and
;;   a needle found there refuses rather than passes;
;;
;;   page bodies are decoded with a fatal UTF-8 decoder, because this host
;;   sends a bare text/html and declares its encoding only in the document --
;;   a mojibake needle failure is not a finding;
;;
;;   registry files are checked on status, content-type and magic bytes,
;;   because a deleted one answers 404 with 36 KB of HTML, and on still being
;;   LINKED, because a file nothing cites any more is an orphaned citation
;;   that no check on the file alone can see.
;;
;; ── SELF-TESTS ASSERT THE REASON, NOT THE VERDICT
;;
;; A negative test that only asserts this failed counts a failure for the
;; wrong cause as a success. Every self-test below names the reason keyword it
;; expects, and the run REFUSES if a self-test returns any other reason --
;; including :ok. Each is paired with the opposite direction wherever a check
;; could otherwise be satisfied by a constant.

(ns verify-facts
  (:require ["fs" :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def ^:private ua "cloud-itonami-iso3166-jpn-fsa facts verifier")

;; ── fetching ───────────────────────────────────────────────────────────────

(defn- decode-utf8-strict
  "nil when the bytes are not valid UTF-8. The caller must treat nil as
   REFUSED, never as an empty page: the difference between a dead citation and
   an unreadable one is the whole reason exit 2 exists."
  [buf]
  (try (.decode (js/TextDecoder. "utf-8" #js {:fatal true}) buf)
       (catch :default _ nil)))

(defn- fetch-bytes
  "Bytes, not text. Decoding is a decision each check makes for itself: the
   registry files are not text at all, and getting that wrong is silent."
  [url]
  (-> (js/fetch url #js {:redirect "follow" :headers #js {"User-Agent" ua}})
      (.then (fn [r] (.then (.arrayBuffer r)
                            (fn [ab] {:status (.-status r)
                                      :final-url (.-url r)
                                      :ctype (or (.get (.-headers r) "content-type") "")
                                      :bytes (js/Uint8Array. ab)}))))
      (.catch (fn [e] {:error (str e)}))))

(defn- fetch-json
  "Parsed JSON, and the status alongside it. The status is returned but the
   statute check must not branch on it -- see the header."
  [url]
  (-> (js/fetch url #js {:redirect "follow" :headers #js {"User-Agent" ua}})
      (.then (fn [r]
               (.then (.text r)
                      (fn [t]
                        (try {:status (.-status r) :json (js->clj (js/JSON.parse t))}
                             (catch :default _
                               {:status (.-status r) :unparseable (subs t 0 200)}))))))
      (.catch (fn [e] {:error (str e)}))))

(defn- fetch-status [url]
  (-> (js/fetch url #js {:redirect "follow" :headers #js {"User-Agent" ua}})
      (.then (fn [r] {:status (.-status r)}))
      (.catch (fn [e] {:error (str e)}))))

;; ── html ───────────────────────────────────────────────────────────────────

(defn- page-title [text]
  (when-let [m (re-find #"(?is)<title[^>]*>(.*?)</title>" (or text ""))]
    (str/trim (str/replace (second m) #"\s+" " "))))

(defn- de-tag
  "Text with tags, scripts and styles removed and whitespace collapsed.
   Needle tests run on this, not on raw HTML -- a needle that only matches
   inside an attribute, a script block or a URL is not the page saying it.
   Scripts matter here specifically: this host inlines analytics config, and
   leaving it in put roughly 6,000 characters of noise into every comparison
   when this file was being measured."
  [text]
  (-> (or text "")
      (str/replace #"(?is)<script.*?</script>" " ")
      (str/replace #"(?is)<style.*?</style>" " ")
      (str/replace #"<[^>]+>" " ")
      (str/replace #"\s+" " ")))

;; ── hosts ──────────────────────────────────────────────────────────────────

(defn- measure-page-host
  "Prove this host's missing-page answer still discriminates, and capture the
   404 body so needles can be subtracted from it.

   Also re-proves the header's claim that the 404 is longer than the front
   page. That is not decoration: it is the reason needles are chosen by
   subtraction here at all, and if it ever stops being true this file's
   reasoning is stale and should be re-measured rather than trusted."
  [h]
  (let [probe (str (:host/missing-probe h) "zzz-no-such-page-9f3c2a1b8e/")]
    (-> (fetch-bytes probe)
        (.then
         (fn [miss]
           (-> (fetch-bytes (:host/front-page h))
               (.then
                (fn [live]
                  (let [miss-text (when-not (:error miss) (decode-utf8-strict (:bytes miss)))
                        live-text (when-not (:error live) (decode-utf8-strict (:bytes live)))]
                    (cond
                      (:error miss) {:refuse (str "could not reach " probe " -- " (:error miss))}
                      (:error live) {:refuse (str "could not reach the front page -- " (:error live))}

                      ;; A fabricated path answering 200 means this host has
                      ;; started serving soft 404s, and every page check below
                      ;; leans on status. REFUSED, not failed.
                      (not= (:status miss) (:host/missing-status h))
                      {:refuse (str "fabricated path " probe " answered " (:status miss)
                                    ", register says " (:host/missing-status h)
                                    ". Page checks on this host cannot be trusted.")}

                      (nil? miss-text)
                      {:refuse (str "this host's 404 body is no longer valid UTF-8. "
                                    "Needles cannot be subtracted from it, so no page "
                                    "check on " (:host/name h) " can be trusted this run.")}

                      (not= (page-title miss-text) (:host/missing-title h))
                      {:refuse (str "missing-page title is now " (pr-str (page-title miss-text))
                                    ", register says " (pr-str (:host/missing-title h)))}

                      (nil? live-text)
                      {:refuse "the front page is no longer valid UTF-8"}

                      (= (page-title miss-text) (page-title live-text))
                      {:refuse "missing page and front page now share a title; the 404 no longer discriminates"}

                      :else
                      (let [mt (de-tag miss-text) lt (de-tag live-text)]
                        (if (and (:host/missing-longer-than-front? h)
                                 (<= (count mt) (count lt)))
                          {:refuse (str "the 404 body (" (count mt) " chars) is no longer longer "
                                        "than the front page (" (count lt) " chars). facts.edn's "
                                        "header reasons from that being true; re-measure this host "
                                        "rather than trusting the needles chosen under it.")}
                          {:missing-status (:status miss)
                           :missing-title (page-title miss-text)
                           :missing-text mt
                           :front-chars (count lt)}))))))))))))

;; ── statutes ───────────────────────────────────────────────────────────────

(defn- law-row
  "The single law record for an id, or a reason. NEVER branches on HTTP
   status: this endpoint answers 200 for a fabricated id (facts.edn header),
   so status carries no information about existence and reading it would make
   every fabricated id in the register verify."
  [law-id api-base]
  (-> (fetch-json (str api-base law-id))
      (.then
       (fn [r]
         (cond
           (:error r) {:reason :unreachable :detail (:error r)}
           (:unparseable r) {:reason :unreachable
                             :detail (str "non-JSON from the statute API: " (:unparseable r))}
           :else
           (let [j (:json r)
                 total (get j "total_count")
                 laws (get j "laws")]
             (cond
               (not= 1 total)
               {:reason :law-missing
                :detail (str law-id " does not resolve: total_count is " (pr-str total)
                             " (the endpoint answered HTTP " (:status r)
                             ", which it does for fabricated ids too)")}

               (empty? laws)
               {:reason :law-missing
                :detail (str law-id " reported total_count 1 with an empty laws array")}

               :else {:row (first laws)})))))))

(defn- check-statute [e api-base]
  (-> (law-row (:statute/law-id e) api-base)
      (.then
       (fn [r]
         (if (:reason r)
           r
           (let [row (:row r)
                 li (get row "law_info")
                 ri (get row "revision_info")
                 got-id (get li "law_id")
                 got-title (get ri "law_title")
                 got-num (get li "law_num")
                 got-kind (get li "law_type")
                 got-prom (get li "promulgation_date")
                 repeal (get ri "repeal_status")
                 revst (get ri "current_revision_status")
                 repealed-now? (not= "None" repeal)]
             (cond
               (not= got-id (:statute/law-id e))
               {:reason :law-id-mismatch
                :detail (str "asked for " (:statute/law-id e) ", got " (pr-str got-id))}

               (not= got-title (:statute/title e))
               {:reason :law-title-changed
                :detail (str (:statute/law-id e) " is titled " (pr-str got-title)
                             ", register says " (pr-str (:statute/title e)))}

               (not= got-num (:statute/law-num e))
               {:reason :law-num-changed
                :detail (str (:statute/law-id e) " has law number " (pr-str got-num)
                             ", register says " (pr-str (:statute/law-num e)))}

               (not= got-kind (:statute/kind e))
               {:reason :law-kind-changed
                :detail (str (:statute/law-id e) " is of type " (pr-str got-kind)
                             ", register says " (pr-str (:statute/kind e)))}

               (not= got-prom (:statute/promulgated e))
               {:reason :law-promulgated-changed
                :detail (str (:statute/law-id e) " was promulgated " (pr-str got-prom)
                             ", register says " (pr-str (:statute/promulgated e)))}

               (not= repealed-now? (boolean (:statute/repealed? e)))
               {:reason :repeal-changed
                :detail (str (:statute/law-id e) " has repeal_status " (pr-str repeal)
                             ", register says :statute/repealed? " (pr-str (boolean (:statute/repealed? e))))}

               ;; The exact token, where the register names one. Repeal,
               ;; Expire and LossOfEffectiveness are three different states
               ;; and collapsing them into "repealed" loses the distinction
               ;; the controls exist to hold open.
               (and (:statute/repeal-token e) (not= repeal (:statute/repeal-token e)))
               {:reason :repeal-token-changed
                :detail (str (:statute/law-id e) " repeal token is " (pr-str repeal)
                             ", register says " (pr-str (:statute/repeal-token e)))}

               ;; The second field. repeal_status None is NOT in force -- see
               ;; :law/previous-enforced-control, which is exactly this case.
               (not= (= "CurrentEnforced" revst) (boolean (:statute/enforced? e)))
               {:reason :enforcement-changed
                :detail (str (:statute/law-id e) " has current_revision_status " (pr-str revst)
                             ", register says :statute/enforced? "
                             (pr-str (boolean (:statute/enforced? e)))
                             (when (and (= "None" repeal) (not= "CurrentEnforced" revst))
                               " (note repeal_status is None -- not repealed, but not the current revision either)"))}

               :else {:reason :ok
                      :detail (str got-title " " got-num " repeal=" repeal " rev=" revst)}))))))) 

(defn- check-fabricated-id
  "The two-endpoint control. Asserts the listing endpoint still answers a
   fabricated id with 200 and total_count 0, and that law_data still 404s.
   This is what keeps the header's claim honest -- and what would tell us the
   day the cheap endpoint became safe to status-check."
  [e host]
  (-> (fetch-json (str (:host/api-base host) (:statute/law-id e)))
      (.then
       (fn [r]
         (cond
           (:error r) {:reason :unreachable :detail (:error r)}
           (not= (:status r) (:statute/listing-status e))
           {:reason :control-drift
            :detail (str "the listing endpoint now answers " (:status r)
                         " for the fabricated id " (:statute/law-id e)
                         ", register says " (:statute/listing-status e)
                         ". facts.edn's two-endpoint note is stale.")}
           (not= (get (:json r) "total_count") (:statute/listing-total e))
           {:reason :control-drift
            :detail (str "the listing endpoint returned total_count "
                         (pr-str (get (:json r) "total_count"))
                         " for a fabricated id, register says " (:statute/listing-total e))}
           :else
           (-> (fetch-status (str (:host/full-text-base host) (:statute/law-id e)))
               (.then (fn [t]
                        (cond
                          (:error t) {:reason :unreachable :detail (:error t)}
                          (not= (:status t) (:statute/full-text-status e))
                          {:reason :control-drift
                           :detail (str "law_data now answers " (:status t)
                                        " for the fabricated id, register says "
                                        (:statute/full-text-status e))}
                          :else
                          {:reason :ok
                           :detail (str "listing " (:status r) "/total 0, law_data "
                                        (:status t) " -- the two endpoints still disagree")}))))))))) 

;; ── pages ──────────────────────────────────────────────────────────────────

(defn- check-page
  "Status, exact title, needle present -- and needle ABSENT from the live 404.
   The last one is the check this host forces. Its 404 carries the whole
   navigation, so a needle that becomes chrome starts matching a deleted page,
   and a verifier that only asked is the needle present would pass forever
   without reading anything."
  [e missing]
  (-> (fetch-bytes (:page/url e))
      (.then
       (fn [r]
         (cond
           (:error r) {:reason :unreachable :detail (:error r)}

           (not= 200 (:status r))
           {:reason :status
            :detail (str "HTTP " (:status r) " at " (:page/url e)
                         ", register records this page as answering 200")}

           :else
           (let [text (decode-utf8-strict (:bytes r))]
             (cond
               (nil? text)
               {:reason :undecodable
                :detail (str (:page/url e) " is not valid UTF-8. This host sends a bare "
                             "text/html and declares encoding only in the document, so this "
                             "is a reading failure, not a finding about the page.")}

               (not= (page-title text) (:page/title e))
               {:reason :title
                :detail (str "title is now " (pr-str (page-title text))
                             ", register says " (pr-str (:page/title e)))}

               ;; Order matters. The chrome test runs BEFORE the verdict, so a
               ;; needle that is on both the page and the 404 refuses instead
               ;; of passing. Checked the other way round it would pass, which
               ;; is the failure this whole file is arranged against.
               (str/includes? (:missing-text missing) (:page/must-contain e))
               {:reason :needle-on-404
                :detail (str (pr-str (:page/must-contain e)) " is on this host's 404 body, so "
                             "it cannot establish that " (:page/url e) " is the page the "
                             "register means. Choose a needle absent from the 404.")}

               (not (str/includes? (de-tag text) (:page/must-contain e)))
               {:reason :needle-missing
                :detail (str (pr-str (:page/must-contain e)) " is not on the page. The page is "
                             "live and readable and its title still matches, so this is a "
                             "change in the page rather than a reading failure.")}

               :else {:reason :ok :detail (page-title text)})))))))

;; ── registry files ─────────────────────────────────────────────────────────

(defn- pdf-magic? [bytes]
  (and bytes (>= (.-length bytes) 5)
       (= "%PDF-" (apply str (map #(js/String.fromCharCode (aget bytes %)) (range 5))))))

(defn- check-registry
  "Three independent properties of the file, then one about the page that
   cites it.

   The three are not redundant. A deleted registry answers 404 with
   content-type text/html and a 36 KB HTML body, so status alone is enough
   for that case -- but a file served with a changed content-type, or an
   HTML error document served at 200, each fail a different one of the three,
   and reporting which one failed is the difference between a finding and a
   shrug."
  [e]
  (-> (fetch-bytes (:registry/url e))
      (.then
       (fn [r]
         (cond
           (:error r) {:reason :unreachable :detail (:error r)}

           (not= 200 (:status r))
           {:reason :pdf-status
            :detail (str "HTTP " (:status r) " at " (:registry/url e)
                         " (a deleted registry answers 404 with an HTML body)")}

           (not (str/includes? (str/lower-case (:ctype r)) (:registry/content-type e)))
           {:reason :pdf-content-type
            :detail (str "content-type is " (pr-str (:ctype r))
                         ", register says " (pr-str (:registry/content-type e)))}

           (not (pdf-magic? (:bytes r)))
           {:reason :pdf-not-pdf
            :detail (str (:registry/url e) " answered 200 with content-type "
                         (pr-str (:ctype r)) " but does not begin %PDF-")}

           :else
           ;; The file exists. Does anything still cite it?
           (-> (fetch-bytes (:registry/linked-from e))
               (.then (fn [p]
                        (let [t (when-not (:error p) (decode-utf8-strict (:bytes p)))]
                          (cond
                            (:error p) {:reason :unreachable :detail (:error p)}
                            (not= 200 (:status p))
                            {:reason :unreachable
                             :detail (str "the citing page " (:registry/linked-from e)
                                          " answered " (:status p)
                                          "; cannot tell whether this file is still linked")}
                            (nil? t) {:reason :undecodable
                                      :detail (str (:registry/linked-from e) " is not valid UTF-8")}
                            (not (str/includes? t (:registry/link-path e)))
                            {:reason :pdf-orphaned
                             :detail (str (:registry/url e) " is still served, but "
                                          (:registry/linked-from e) " no longer links "
                                          (:registry/link-path e) ". The file outlived its "
                                          "citation, which no check on the file alone can see.")}
                            :else
                            {:reason :ok
                             :detail (str (.-length (:bytes r)) " bytes, "
                                          (:ctype r) ", linked")}))))))))))

;; ── self-tests ─────────────────────────────────────────────────────────────
;;
;; Fixtures are real. The repealed control really is repealed, the
;; PreviousEnforced control really does carry repeal_status None, and the
;; chrome needle really is on this host's 404 -- so each test exercises the
;; trap it names on the live surface rather than on a synthetic stand-in.

(defn- self-tests [api-base host missing]
  (let [want (fn [label expected p]
               (.then p (fn [r] {:label label :expected expected :got (:reason r)
                                 :ok? (= (:reason r) expected) :detail (:detail r)})))
        L (fn [m] (merge {:statute/kind "Act"} m))
        pay {:statute/law-id "421AC0000000059" :statute/title "資金決済に関する法律"
             :statute/law-num "平成二十一年法律第五十九号" :statute/kind "Act"
             :statute/promulgated "2009-06-24" :statute/repealed? false :statute/enforced? true}
        repealed {:statute/law-id "324AC0000000068" :statute/title "簡易生命保険法"
                  :statute/law-num "昭和二十四年法律第六十八号" :statute/kind "Act"
                  :statute/promulgated "1949-05-16" :statute/repealed? true
                  :statute/repeal-token "Repeal" :statute/enforced? false}
        prev {:statute/law-id "329AC0000000115" :statute/title "厚生年金保険法"
              :statute/law-num "昭和二十九年法律第百十五号" :statute/kind "Act"
              :statute/promulgated "1954-05-19" :statute/repealed? false :statute/enforced? false}
        law-page {:page/url "https://www.fsa.go.jp/common/law/index.html"
                  :page/title "法令・指針等：金融庁" :page/must-contain "所管法令一覧"}
        real-pdf {:registry/url "https://www.fsa.go.jp/menkyo/menkyoj/shikin_idou.pdf"
                  :registry/content-type "application/pdf"
                  :registry/linked-from "https://www.fsa.go.jp/menkyo/menkyo.html"
                  :registry/link-path "/menkyo/menkyoj/shikin_idou.pdf"}]
    (js/Promise.all
     #js
     [;; 1. A fabricated law id must be reported missing -- and note this can
      ;;    ONLY come from total_count, because the endpoint answers it 200.
      (want "fabricated law id -> :law-missing" :law-missing
            (check-statute (L {:statute/law-id "999AC9999999999" :statute/title "存在しない法律"
                               :statute/law-num "x" :statute/promulgated "1900-01-01"
                               :statute/repealed? false :statute/enforced? true}) api-base))

      ;; 2. A real in-force law must pass. Without this, test 1 also passes
      ;;    against a verifier that reports everything missing.
      (want "real in-force law -> :ok" :ok (check-statute pay api-base))

      ;; 3. An in-force law declared repealed must be caught.
      (want "in-force law declared repealed -> :repeal-changed" :repeal-changed
            (check-statute (assoc pay :statute/repealed? true) api-base))

      ;; 4. The repealed control declared live. The API serves it at
      ;;    total_count 1 with full metadata, so only the repeal field can
      ;;    catch it.
      (want "repealed control declared live -> :repeal-changed" :repeal-changed
            (check-statute (assoc repealed :statute/repealed? false :statute/enforced? true) api-base))

      ;; 5. ...and declared repealed it must pass. 4 and 5 together are what
      ;;    make the repeal check a check rather than a constant.
      (want "repealed control declared repealed -> :ok" :ok (check-statute repealed api-base))

      ;; 6. THE SECOND FIELD. This law has repeal_status None -- it was never
      ;;    repealed -- but the served revision is PreviousEnforced. Declared
      ;;    in force, it must be caught, and it can only be caught by the
      ;;    revision field. A verifier reading repeal_status alone passes it.
      (want "not-repealed but superseded, declared in force -> :enforcement-changed" :enforcement-changed
            (check-statute (assoc prev :statute/enforced? true) api-base))

      ;; 7. ...and declared not-in-force it must pass, so 6 is not satisfied
      ;;    by a verifier that rejects everything on this field.
      (want "not-repealed but superseded, declared superseded -> :ok" :ok
            (check-statute prev api-base))

      ;; 8. A real law id under the wrong title, or an entry could name one
      ;;    law and cite another.
      (want "right id, wrong title -> :law-title-changed" :law-title-changed
            (check-statute (assoc pay :statute/title "銀行法") api-base))

      ;; 9. A real law id under the wrong law number. Titles can be shared
      ;;    across instruments; the number is what pins the instrument.
      (want "right id, wrong law number -> :law-num-changed" :law-num-changed
            (check-statute (assoc pay :statute/law-num "平成二十一年法律第六十号") api-base))

      ;; 10. The two-endpoint control itself.
      (want "fabricated-id control -> :ok" :ok
            (check-fabricated-id {:statute/law-id "999AC0000000999" :statute/listing-status 200
                                  :statute/listing-total 0 :statute/full-text-status 404} host))

      ;; 11. ...and the control must notice if the endpoints stop disagreeing.
      (want "control asserting law_data 200 -> :control-drift" :control-drift
            (check-fabricated-id {:statute/law-id "999AC0000000999" :statute/listing-status 200
                                  :statute/listing-total 0 :statute/full-text-status 200} host))

      ;; 12. A fabricated page must be caught by status.
      (want "fabricated page -> :status" :status
            (check-page {:page/url "https://www.fsa.go.jp/common/law/zzz-9f3c2a1b8e.html"
                         :page/title "どれでもない" :page/must-contain "存在しない文字列"}
                        missing))

      ;; 13. A real page under a stale title.
      (want "real page, stale title -> :title" :title
            (check-page (assoc law-page :page/title "金融庁") missing))

      ;; 14. THE CHROME TRAP, on the real surface. 銀行 is genuinely on this
      ;;     host's 404 body six times, so pointed at a real page with it as
      ;;     the needle the check must REFUSE -- not pass, which is what a
      ;;     needle-present-only check does, and not fail, which would claim
      ;;     something about the page.
      (want "needle that is site chrome -> :needle-on-404" :needle-on-404
            (check-page (assoc law-page :page/must-contain "銀行") missing))

      ;; 15. ...and a needle genuinely absent from a live, readable page must
      ;;     FAIL, not refuse. :needle-missing and :needle-on-404 are
      ;;     different claims and must not collapse into each other.
      (want "absent needle on a readable page -> :needle-missing" :needle-missing
            (check-page (assoc law-page :page/must-contain "この文字列はこのページにない9f3c") missing))

      ;; 16. The ordinary case, so 12-15 are not passed by a verifier that
      ;;     never returns :ok for a page.
      (want "real page, real needle -> :ok" :ok (check-page law-page missing))

      ;; 17. A deleted registry file. Answers 404 with an HTML body, so this
      ;;     must come back on status rather than on the magic bytes.
      (want "deleted registry file -> :pdf-status" :pdf-status
            (check-registry (assoc real-pdf :registry/url
                                   "https://www.fsa.go.jp/menkyo/menkyoj/no_such_file_9f3c2a.pdf")))

      ;; 18. An HTML page cited as a registry file. 200, real body, wrong
      ;;     kind of thing -- the case status cannot see.
      (want "html cited as a registry file -> :pdf-content-type" :pdf-content-type
            (check-registry (assoc real-pdf :registry/url "https://www.fsa.go.jp/menkyo/menkyo.html")))

      ;; 19. A real file that its citing page does not link. The file passes
      ;;     all three of its own checks; only the page can see this.
      (want "registry file nothing links -> :pdf-orphaned" :pdf-orphaned
            (check-registry (assoc real-pdf :registry/linked-from
                                   "https://www.fsa.go.jp/common/law/index.html")))

      ;; 20. The ordinary case, so 17-19 are not passed by a verifier that
      ;;     never returns :ok for a registry file.
      (want "real registry file, still linked -> :ok" :ok (check-registry real-pdf))])))

;; ── main ───────────────────────────────────────────────────────────────────

(def ^:private refuse-reasons
  "Reasons that mean this run could not answer, as opposed to answering no."
  #{:unreachable :undecodable :needle-on-404 :control-drift})

(defn- main []
  (let [register (edn/read-string (fs/readFileSync "facts.edn" "utf8"))
        hosts (into {} (map (juxt :host/name identity)) (filter :host/name register))
        page-host (get hosts "www.fsa.go.jp")
        api-host (get hosts "laws.e-gov.go.jp")
        statutes (filterv #(= :statute-api (:source/verify %)) register)
        fabricated (filterv #(= :fabricated-id (:source/verify %)) register)
        pages (filterv #(= :page (:source/verify %)) register)
        registries (filterv #(= :registry-pdf (:source/verify %)) register)]

    ;; Structural floors. A register that lost a whole entry kind, or a
    ;; reduction that removed one, must not be able to report a clean run --
    ;; nothing checked and everything checked would otherwise print alike.
    (cond
      (nil? page-host) (do (println "REFUSED — facts.edn has no www.fsa.go.jp host entry") (js/process.exit 2))
      (nil? api-host) (do (println "REFUSED — facts.edn has no laws.e-gov.go.jp host entry") (js/process.exit 2))
      (zero? (count statutes)) (do (println "REFUSED — no statute entries") (js/process.exit 2))
      (zero? (count pages)) (do (println "REFUSED — no page entries") (js/process.exit 2))
      (zero? (count registries)) (do (println "REFUSED — no registry entries") (js/process.exit 2))
      (zero? (count fabricated)) (do (println "REFUSED — the fabricated-id control is missing; "
                                              "the statute checks would be unguarded") (js/process.exit 2))
      ;; The controls are what make the repeal and revision checks
      ;; discriminate. Losing them is silent otherwise.
      (not (some :statute/repeal-token statutes))
      (do (println "REFUSED — no repealed control in the register") (js/process.exit 2))
      (not (some #(and (false? (:statute/repealed? %)) (false? (:statute/enforced? %))) statutes))
      (do (println "REFUSED — no not-repealed-but-superseded control in the register") (js/process.exit 2)))

    (-> (measure-page-host page-host)
        (.then
         (fn [missing]
           (if (:refuse missing)
             (do (println (str "REFUSED — www.fsa.go.jp: " (:refuse missing)))
                 (js/process.exit 2))
             (do
               (println (str "host www.fsa.go.jp: 404 " (:missing-status missing)
                             " " (pr-str (:missing-title missing))
                             " — 404 body " (count (:missing-text missing))
                             " chars vs front page " (:front-chars missing) " chars"))
               (-> (self-tests (:host/api-base api-host) api-host missing)
                   (.then
                    (fn [sts]
                      (let [sts (js->clj sts) bad (remove :ok? sts)]
                        (println (str "self-tests: " (- (count sts) (count bad)) "/" (count sts) " ok"))
                        (when (seq bad)
                          (doseq [b bad]
                            (println (str "  SELF-TEST FAILED — " (:label b)
                                          " expected " (:expected b) " got " (:got b)
                                          " :: " (:detail b))))
                          (println (str "REFUSED — " (count bad) " self-test(s) returned the wrong "
                                        "reason. The checks are not discriminating, so nothing this "
                                        "run says about the register can be trusted."))
                          (js/process.exit 2))

                        (-> (js/Promise.all
                             (clj->js
                              (concat
                               (map (fn [e] (.then (check-statute e (:host/api-base api-host))
                                                   #(assoc % :id (:source/id e)))) statutes)
                               (map (fn [e] (.then (check-fabricated-id e api-host)
                                                   #(assoc % :id (:source/id e)))) fabricated)
                               (map (fn [e] (.then (check-page e missing)
                                                   #(assoc % :id (:source/id e)))) pages)
                               (map (fn [e] (.then (check-registry e)
                                                   #(assoc % :id (:source/id e)))) registries))))
                            (.then
                             (fn [results]
                               (let [results (js->clj results :keywordize-keys true)
                                     refused (filter #(refuse-reasons (:reason %)) results)
                                     failed (filter #(and (not= :ok (:reason %))
                                                          (not (refuse-reasons (:reason %)))) results)]
                                 (doseq [r results]
                                   (println (str (case (:reason r)
                                                   :ok "  ok      "
                                                   (if (refuse-reasons (:reason r)) "  REFUSED " "  FAIL    "))
                                                 (:id r) "  " (:reason r) "  " (:detail r))))
                                 (println (str "checked " (count results) " entries: "
                                               (count statutes) " statutes, "
                                               (count fabricated) " control, "
                                               (count pages) " pages, "
                                               (count registries) " registry files"))
                                 (cond
                                   (seq refused)
                                   (do (println (str "REFUSED — " (count refused)
                                                     " entry/entries could not be answered"))
                                       (js/process.exit 2))
                                   (seq failed)
                                   (do (println (str "FAIL — " (count failed)
                                                     " entry/entries disagree with the register"))
                                       (js/process.exit 1))
                                   :else
                                   (println (str "OK — all " (count results)
                                                 " entries agree with the live authorities"))))))))))))))))))

(main)
