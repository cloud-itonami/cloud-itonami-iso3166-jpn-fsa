;; Prove verify-facts.cljs discriminates, by breaking facts.edn on purpose.
;;
;;   nbb scripts/break-tests.cljs
;;
;; ── WHY THIS EXISTS SEPARATELY FROM THE SELF-TESTS
;;
;; verify-facts.cljs already self-tests its checks, and refuses if any of them
;; returns the wrong reason. That establishes the CHECKS work. It does not
;; establish that the checks are wired to the REGISTER -- a verifier whose
;; self-tests all pass while its main loop reads the wrong field, skips an
;; entry kind, or maps every failure onto one exit code would look exactly
;; like this one from the outside.
;;
;; So each case below breaks one entry in the register as DATA -- reading
;; facts.edn, editing the parsed entity, writing it back -- and asserts three
;; things about the run: the exit code, the reason reported, and that the
;; reason is attached to the entry that was broken.
;;
;; Editing as data rather than by string replacement is deliberate. A textual
;; break can land in a comment, in a docstring, or on a substring of some
;; other entry, and then the red that comes back is a red for the wrong
;; reason -- which reads exactly like a successful demonstration. This
;; register is mostly comment by volume, so that is not a hypothetical risk
;; here: nearly every string these cases touch also appears in the header.
;;
;; ── THE CONTROL IS PART OF THE TEST
;;
;; Every case runs against a reduced register so that twenty-odd runs are
;; affordable. A reduction can itself break things, so case 0 runs the
;; reduced register UNMODIFIED and requires exit 0. Without it, every later
;; red could be an artefact of the fixture.
;;
;; ── COVERAGE IS ASSERTED, NOT ASSUMED
;;
;; At the end this file checks that every reason keyword verify-facts.cljs
;; can return is exercised by some case, and fails if one is not. A reason
;; that no case produces is a check nobody has ever seen fire.

(ns break-tests
  (:require ["fs" :as fs]
            ["os" :as os]
            ["path" :as path]
            ["child_process" :as cp]
            [clojure.edn :as edn]
            [clojure.string :as str]))

(def ^:private register (edn/read-string (fs/readFileSync "facts.edn" "utf8")))
(def ^:private verifier (fs/readFileSync "scripts/verify-facts.cljs" "utf8"))

(defn- by-id [id] (first (filter #(= id (:source/id %)) register)))
(def ^:private hosts (filterv :host/name register))

(def ^:private statute (by-id :law/payment-services))
(def ^:private repealed (by-id :law/repealed-control))
(def ^:private superseded (by-id :law/previous-enforced-control))
(def ^:private superseded-2 (by-id :law/previous-enforced-control-2))
(def ^:private fabricated (by-id :law/fabricated-id-control))
(def ^:private page (by-id :page/law-index))
(def ^:private registry (by-id :registry/funds-transfer))

(defn- fixture
  "Two hosts plus one entry of each kind the verifier's structural floors
   require. It cannot be made smaller than this without turning the floors
   themselves into the finding -- which is what cases 18-21 do on purpose."
  ([] (fixture {}))
  ([{:keys [statute* repealed* superseded* fabricated* page* registry* hosts* drop extra]
     :or {statute* statute repealed* repealed superseded* superseded
          fabricated* fabricated page* page registry* registry hosts* hosts}}]
   (let [all (concat hosts* [statute* repealed* superseded* fabricated* page* registry*]
                     (or extra []))]
     (vec (remove #(and drop (drop (:source/id %))) all)))))

(defn- run!
  "Write the register into a scratch directory and run the verifier there.
   Returns {:exit :out}. Captured from a file, not a pipe -- $? on a pipeline
   is the last command's status, and a verifier killed by a timeout would
   otherwise be indistinguishable from one that answered."
  [entities]
  (let [dir (fs/mkdtempSync (path/join (os/tmpdir) "fsa-break-"))]
    (fs/mkdirSync (path/join dir "scripts"))
    (fs/writeFileSync (path/join dir "facts.edn") (pr-str entities))
    (fs/writeFileSync (path/join dir "scripts/verify-facts.cljs") verifier)
    (let [r (cp/spawnSync "nbb" #js ["scripts/verify-facts.cljs"]
                          #js {:cwd dir :encoding "utf8" :timeout 240000})]
      (fs/rmSync dir #js {:recursive true :force true})
      {:exit (if (nil? (.-status r)) :killed (.-status r))
       :out (str (.-stdout r) (.-stderr r))})))

;; ── the cases ──────────────────────────────────────────────────────────────
;;
;; :expect-exit  0 = agreed, 1 = a claim about the world, 2 = REFUSED
;; :expect-in    strings that must ALL appear in the output. Always includes
;;               the reason, and where the reason attaches to a specific
;;               entry, that entry's id -- so a run that went red somewhere
;;               else does not count as a demonstration.

(def ^:private cases
  [{:label "0. CONTROL — the reduced register, unmodified"
    :entities (fixture)
    :expect-exit 0 :expect-in ["OK — all"]}

   ;; ── statute identity ────────────────────────────────────────────────
   ;; Every one of these would pass a status check, because the listing
   ;; endpoint answers 200 for all of them including the fabricated id.

   {:label "1. fabricated law id"
    :entities (fixture {:statute* (assoc statute :statute/law-id "999AC9999999999")})
    :expect-exit 1 :expect-in [":law/payment-services" "law-missing" "does not resolve"]}

   {:label "2. real law id, wrong title"
    :entities (fixture {:statute* (assoc statute :statute/title "銀行法")})
    :expect-exit 1 :expect-in [":law/payment-services" "law-title-changed" "register says"]}

   {:label "3. real law id, wrong law number"
    :entities (fixture {:statute* (assoc statute :statute/law-num "平成二十一年法律第六十号")})
    :expect-exit 1 :expect-in [":law/payment-services" "law-num-changed"]}

   {:label "4. real law id, wrong instrument type"
    :entities (fixture {:statute* (assoc statute :statute/kind "CabinetOrder")})
    :expect-exit 1 :expect-in [":law/payment-services" "law-kind-changed"]}

   {:label "5. real law id, wrong promulgation date"
    :entities (fixture {:statute* (assoc statute :statute/promulgated "2009-06-25")})
    :expect-exit 1 :expect-in [":law/payment-services" "law-promulgated-changed"]}

   ;; ── the two fields that decide whether a law is operative ───────────

   {:label "6. an in-force Act declared repealed"
    :entities (fixture {:statute* (assoc statute :statute/repealed? true)})
    :expect-exit 1 :expect-in [":law/payment-services" "repeal-changed" "repeal_status"]}

   {:label "7. the repealed control declared live"
    :entities (fixture {:repealed* (assoc repealed :statute/repealed? false
                                          :statute/enforced? true)})
    :expect-exit 1 :expect-in [":law/repealed-control" "repeal-changed"]}

   {:label "8. the repealed control under the wrong repeal token"
    :entities (fixture {:repealed* (assoc repealed :statute/repeal-token "Expire")})
    :expect-exit 1 :expect-in [":law/repealed-control" "repeal-token-changed"]}

   {:label "9a. an in-force Act declared superseded. repeal_status agrees with
            the register in both directions here, so only the revision field
            can see it."
    :entities (fixture {:statute* (assoc statute :statute/enforced? false)})
    :expect-exit 1 :expect-in [":law/payment-services" "enforcement-changed"
                               "CurrentEnforced"]}

   ;; The direction that matters more, and the one that needed a second
   ;; control to be testable at all. repeal_status on this law is None -- it
   ;; was never repealed -- so a verifier reading only that field calls it in
   ;; force and hands back superseded text. ONLY current_revision_status
   ;; catches it.
   ;;
   ;; Written first against the single control, this case came back REFUSED
   ;; instead: mis-declaring the only superseded entry removes the control,
   ;; the structural floor notices that before any entry is checked, and
   ;; :enforcement-changed never fired. The floor was right and the case was
   ;; wrong. A second correct control keeps the floor satisfied while this one
   ;; is broken. The coverage assertion at the foot of this file is what made
   ;; the gap visible rather than leaving a check that merely looked tested.
   {:label "9b. the superseded control declared in force, with a second
            superseded control present to satisfy the structural floor"
    :entities (fixture {:superseded* (assoc superseded :statute/enforced? true)
                        :extra [superseded-2]})
    :expect-exit 1 :expect-in [":law/previous-enforced-control" "enforcement-changed"
                               "PreviousEnforced"]}

   {:label "10. the two-endpoint control asserting law_data agrees with the
            listing endpoint"
    :entities (fixture {:fabricated* (assoc fabricated :statute/full-text-status 200)})
    :expect-exit 2 :expect-in [":law/fabricated-id-control" "control-drift" "law_data"]}

   {:label "11. the two-endpoint control asserting the listing endpoint 404s"
    :entities (fixture {:fabricated* (assoc fabricated :statute/listing-status 404)})
    :expect-exit 2 :expect-in [":law/fabricated-id-control" "control-drift"]}

   ;; ── pages ───────────────────────────────────────────────────────────

   {:label "12. fabricated page url"
    :entities (fixture {:page* (assoc page :page/url
                                      "https://www.fsa.go.jp/common/law/zzz-9f3c2a1b8e.html")})
    :expect-exit 1 :expect-in [":page/law-index" "HTTP 404"]}

   {:label "13. stale page title"
    :entities (fixture {:page* (assoc page :page/title "金融庁")})
    :expect-exit 1 :expect-in [":page/law-index" ":title" "register says"]}

   {:label "14. THE CHROME TRAP — a needle that is on this host's 404. Must
            REFUSE: the check is broken, the page is not."
    :entities (fixture {:page* (assoc page :page/must-contain "銀行")})
    :expect-exit 2 :expect-in [":page/law-index" "needle-on-404"]}

   {:label "15. a needle genuinely absent from a live, readable page. Must
            FAIL rather than refuse — a different claim from case 14."
    :entities (fixture {:page* (assoc page :page/must-contain "この文字列はこのページにない9f3c")})
    :expect-exit 1 :expect-in [":page/law-index" "needle-missing"]}

   ;; ── registry files ──────────────────────────────────────────────────

   {:label "16. deleted registry file — answers 404 with 36 KB of HTML"
    :entities (fixture {:registry* (assoc registry :registry/url
                                          "https://www.fsa.go.jp/menkyo/menkyoj/no_such_9f3c2a.pdf")})
    :expect-exit 1 :expect-in [":registry/funds-transfer" "pdf-status"]}

   {:label "17. an HTML page cited as a registry file — 200, real body, wrong
            kind of thing. The case status cannot see."
    :entities (fixture {:registry* (assoc registry :registry/url
                                          "https://www.fsa.go.jp/menkyo/menkyo.html")})
    :expect-exit 1 :expect-in [":registry/funds-transfer" "pdf-content-type"]}

   {:label "18. a live registry file that its citing page no longer links.
            Passes all three of its own checks; only the page sees it."
    :entities (fixture {:registry* (assoc registry :registry/linked-from
                                          "https://www.fsa.go.jp/common/law/index.html")})
    :expect-exit 1 :expect-in [":registry/funds-transfer" "pdf-orphaned"]}

   ;; ── the structural floors ───────────────────────────────────────────
   ;; A register that quietly lost an entry kind, or a control, must not be
   ;; able to report a clean run. These are the cases where "nothing was
   ;; checked" and "everything checked out" would otherwise print alike.

   {:label "19. every registry entry removed"
    :entities (fixture {:drop #{:registry/funds-transfer}})
    :expect-exit 2 :expect-in ["REFUSED" "no registry entries"]}

   {:label "20. the repealed control removed — the repeal check would still
            pass on every remaining entry while having stopped discriminating"
    :entities (fixture {:drop #{:law/repealed-control}})
    :expect-exit 2 :expect-in ["REFUSED" "no repealed control"]}

   {:label "21. the superseded control removed"
    :entities (fixture {:drop #{:law/previous-enforced-control}})
    :expect-exit 2 :expect-in ["REFUSED" "no not-repealed-but-superseded control"]}

   {:label "22. the two-endpoint control removed"
    :entities (fixture {:drop #{:law/fabricated-id-control}})
    :expect-exit 2 :expect-in ["REFUSED" "fabricated-id control is missing"]}

   ;; ── the host measurement itself ─────────────────────────────────────

   {:label "23. the host's 404 title recorded wrongly — no page check on this
            host can be trusted, so the whole run refuses before any entry"
    :entities (fixture {:hosts* (mapv #(if (= "www.fsa.go.jp" (:host/name %))
                                         (assoc % :host/missing-title "エラー404")
                                         %)
                                      hosts)})
    :expect-exit 2 :expect-in ["REFUSED" "missing-page title"]}

   {:label "24. the host's front-page-is-shorter claim inverted — the reasoning
            the needles were chosen under is stale, so refuse rather than
            keep leaning on it"
    :entities (fixture {:hosts* (mapv #(if (= "www.fsa.go.jp" (:host/name %))
                                         (assoc % :host/front-page
                                                "https://www.fsa.go.jp/menkyo/menkyo.html")
                                         %)
                                      hosts)})
    :expect-exit 2 :expect-in ["REFUSED" "no longer longer"]}])

;; Every reason keyword the verifier can attach to an entry. If a case never
;; produces one of these, that check has never been seen to fire.
(def ^:private reasons-that-must-fire
  ["law-missing" "law-title-changed" "law-num-changed" "law-kind-changed"
   "law-promulgated-changed" "repeal-changed" "repeal-token-changed"
   "enforcement-changed" "control-drift" ":status" ":title" "needle-on-404"
   "needle-missing" "pdf-status" "pdf-content-type" "pdf-orphaned"])

(defn- -main []
  (println (str "running " (count cases) " cases against a reduced register\n"))
  (let [results
        (doall
         (for [c cases]
           (let [{:keys [exit out]} (run! (:entities c))
                 missing (remove #(str/includes? out %) (:expect-in c))
                 ok? (and (= exit (:expect-exit c)) (empty? missing))]
             (println (str (if ok? "PASS  " "FAIL  ")
                           (str/replace (:label c) #"\s+" " ")))
             (when-not ok?
               (println (str "        expected exit " (:expect-exit c) ", got " exit))
               (when (seq missing)
                 (println (str "        missing from output: " (pr-str missing))))
               (println (str "        --- output ---\n"
                             (str/join "\n" (map #(str "        " %)
                                                 (take 25 (str/split-lines out)))))))
             (assoc c :ok? ok? :exit exit :out out))))
        failed (remove :ok? results)
        all-out (str/join "\n" (map :out results))
        never-fired (remove #(str/includes? all-out %) reasons-that-must-fire)]

    (println)
    (when (seq never-fired)
      (println (str "COVERAGE GAP — these reasons were never produced by any case: "
                    (pr-str never-fired)))
      (println "A check no case has ever made fire is a check nobody has seen work."))

    (if (or (seq failed) (seq never-fired))
      (do (println (str "FAIL — " (count failed) "/" (count results) " cases wrong, "
                        (count never-fired) " reason(s) never fired"))
          (js/process.exit 1))
      (println (str "OK — " (count results) " cases, every one broke the register in the way "
                    "it names, and all " (count reasons-that-must-fire)
                    " entry-level reasons fired at least once")))))

(-main)
