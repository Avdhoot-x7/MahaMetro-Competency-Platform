"""Seed question bank v2: situation-based questions with believable wrong options, English + Marathi.

Writing standard (see docs/QUESTION_WRITING_GUIDE.md):
  * most questions describe a real work situation and ask what to do first / next / best;
  * wrong options are mistakes staff actually make (wrong order, shortcut, half-right step, rule from another situation);
  * options are similar in length and form - the correct option is not the longest;
  * no "all of the above" / "none of the above"; four options for single-answer and SJT questions.

IMPORTANT - items marked sme=True depend on Maha Metro General & Subsidiary Rules, SOPs or OEM manuals and MUST be
validated by the departmental SME panel before use in an official (T0) test. Marathi text is a working translation
for review by the CoE language editor.
"""

ITEMS: list[dict] = []
PASSAGES: list[dict] = []


def _add(code, comp, qtype, en, mr, *, lvl=3, bloom="Apply", fam="ALL", pool="exam", why="", why_mr="",
         src="", sme=False, **kw):
    ITEMS.append(dict(code=code, comp=comp, qtype=qtype, en=en, mr=mr, lvl=lvl, bloom=bloom,
                      fam=fam.split(",") if isinstance(fam, str) else fam, pool=pool, why=why, why_mr=why_mr,
                      src=src, sme=sme, **kw))


def S(code, comp, en, mr, opts, **kw):            # single correct: opts = [(en, mr, correct)]
    _add(code, comp, "single", en, mr, opts=opts, **kw)


def M(code, comp, en, mr, opts, **kw):            # multiple correct
    _add(code, comp, "multi", en, mr, opts=opts, partial=True, **kw)


def TF(code, comp, en, mr, answer: bool, **kw):
    _add(code, comp, "truefalse", en, mr, opts=[("True", "बरोबर", answer), ("False", "चूक", not answer)], **kw)


def J(code, comp, en, mr, opts, **kw):            # SJT: opts = [(en, mr, weight 0..1)]
    _add(code, comp, "sjt", en, mr, opts=opts, **kw)


def Q(code, comp, en, mr, steps, **kw):           # sequence: steps in the CORRECT order
    _add(code, comp, "sequence", en, mr, steps=steps, partial=True, **kw)


def X(code, comp, en, mr, pairs, **kw):           # match: [((left_en, left_mr), (right_en, right_mr))]
    _add(code, comp, "match", en, mr, pairs=pairs, partial=True, **kw)


def N(code, comp, en, mr, value, tol=0.0, **kw):  # numeric
    _add(code, comp, "numeric", en, mr, value=value, tol=tol, **kw)


def F(code, comp, en, mr, accepted, **kw):        # fill in the blank
    _add(code, comp, "fill_blank", en, mr, accepted=accepted, **kw)


def W(code, comp, en, mr, rubric, rubric_mr="", marks=5, **kw):   # written answer (manual)
    _add(code, comp, "short_text", en, mr, rubric=rubric, rubric_mr=rubric_mr, marks=marks, **kw)


def A(code, comp, en, mr, rubric, rubric_mr="", marks=5, **kw):   # spoken answer (manual)
    _add(code, comp, "audio_response", en, mr, rubric=rubric, rubric_mr=rubric_mr, marks=marks, **kw)


OPS = "TO,SC,OCC"
TECH = "TO,SC,OCC,RS,STA,TPS,EM,CT,PJ,SFS"
NONTECH = "FA,HRA,PCS,COM,IT,CORP"

# ====================================================================== C01 SAFETY CULTURE & EMERGENCY RESPONSE
S("C01-0001", "C01",
  "Smoke and a burning smell are coming from a 415 V distribution panel in a station electrical room. Nobody is hurt. You are the first person there. What should you do first?",
  "स्थानकाच्या विद्युत कक्षातील 415 V वितरण पॅनेलमधून धूर व जळका वास येत आहे. कोणाला इजा नाही. तिथे पोहोचणारे तुम्ही पहिले आहात. तुम्ही प्रथम काय कराल?",
  [("Raise the alarm, inform the Station Controller, and use a CO2 extinguisher only if safe", "इशारा द्या, स्टेशन कंट्रोलरला कळवा आणि सुरक्षित असल्यासच CO2 अग्निशामक वापरा", True),
   ("Open the panel door to find the exact source, so that the report you give is accurate", "अचूक माहिती देता यावी म्हणून पॅनेलचे दार उघडून नेमका स्रोत शोधा", False),
   ("Switch off the incoming breaker yourself and spray the panel with the nearest extinguisher", "स्वतः इनकमिंग ब्रेकर बंद करा आणि जवळच्या अग्निशामकाने पॅनेलवर फवारा", False),
   ("Wait for the E&M maintainer, as only authorised staff may handle electrical panels", "E&M देखभालकर्त्याची वाट पहा, कारण विद्युत पॅनेल फक्त अधिकृत कर्मचारीच हाताळू शकतात", False)],
  why="Alarm and inform first so help and isolation start; fight the fire only if safe, and only with CO2 on electrical equipment. Opening the panel feeds the fire with air; any extinguisher may be water or foam; waiting loses time.",
  why_mr="आधी इशारा व माहिती दिल्याने मदत व विलगीकरण सुरू होते; सुरक्षित असल्यासच आग विझवा, आणि विद्युत उपकरणावर फक्त CO2 वापरा. पॅनेल उघडल्याने आगीला हवा मिळते; जवळचे अग्निशामक पाणी किंवा फोमचे असू शकते; थांबल्याने वेळ जातो.",
  lvl=2, src="Fire safety training manual")
Q("C01-0002", "C01",
  "A passenger collapses on the concourse and is not breathing normally. Put your first actions in the correct order.",
  "कॉन्कोर्सवर एक प्रवासी कोसळतो आणि त्याचा श्वास सामान्य नाही. तुमच्या पहिल्या कृती योग्य क्रमाने लावा.",
  [("Check the area is safe and shout for help", "परिसर सुरक्षित असल्याची खात्री करा व मदतीसाठी हाक द्या"),
   ("Ask someone to call 108 and tell the SCR to bring the AED", "कोणालातरी 108 वर फोन करण्यास व SCR ला AED आणण्यास सांगा"),
   ("Start chest compressions (CPR)", "छातीवर दाब देणे (CPR) सुरू करा"),
   ("Use the AED as soon as it arrives, following its voice prompts", "AED येताच त्याच्या आवाज-सूचनांनुसार वापरा")],
  why="Basic life support order: safety, call for help and the AED, start CPR without delay, defibrillate as soon as the AED arrives.",
  why_mr="मूलभूत जीवनरक्षण क्रम: सुरक्षा, मदत व AED मागवणे, विलंब न करता CPR, AED येताच त्याचा वापर.",
  lvl=2, sme=True, src="First-aid / BLS training; station AED availability to be confirmed")
M("C01-0003", "C01", "Which of these should be reported as a near-miss?", "यापैकी कोणती घटना 'थोडक्यात टळलेली दुर्घटना' (near-miss) म्हणून नोंदवावी?",
  [("A luggage trolley rolls to the platform edge; a passenger stops it", "सामानाची ट्रॉली प्लॅटफॉर्मच्या कडेकडे जाते; एक प्रवासी ती थांबवतो", True),
   ("A tool left on the track is found during the pre-service track check", "सेवापूर्व रूळ-तपासणीत रुळावर राहिलेले साधन सापडते", True),
   ("A contractor's worker is seen on the viaduct edge without a harness", "कंत्राटदाराचा कामगार हार्नेसशिवाय व्हायाडक्टच्या कडेला दिसतो", True),
   ("A station fire drill finishes two minutes faster than its target time", "स्थानकावरील अग्निशमन सराव लक्ष्य वेळेपेक्षा दोन मिनिटे आधी पूर्ण होतो", False),
   ("A passenger says the platform air-conditioning is too cold", "प्रवासी म्हणतो की प्लॅटफॉर्मवरील वातानुकूलन खूप थंड आहे", False)],
  why="A near-miss is an event or unsafe act that could have caused harm. Reporting them lets causes be removed before an accident.",
  why_mr="ज्या घटनेमुळे किंवा असुरक्षित कृतीमुळे हानी होऊ शकली असती ती near-miss. ती नोंदवल्याने अपघातापूर्वीच कारणे दूर करता येतात.")
TF("C01-0004", "C01",
   "Stop-work authority belongs only to supervisors; a technician who sees an unsafe act should report it after the job is finished.",
   "काम थांबवण्याचा अधिकार फक्त पर्यवेक्षकांना आहे; असुरक्षित कृती दिसल्यास तंत्रज्ञाने काम संपल्यानंतर ती कळवावी.", False,
   lvl=2, bloom="Understand", pool="practice",
   why="Every employee may stop work that is unsafe, at once, until it is made safe.", why_mr="असुरक्षित काम कोणताही कर्मचारी ताबडतोब, ते सुरक्षित होईपर्यंत थांबवू शकतो.")
J("C01-0005", "C01",
  "You hear this alarm during peak hour. The fire panel shows one smoke detector in alarm on the concourse; nobody has reported smoke. What is the best first action?",
  "गर्दीच्या वेळी हा इशारा ऐकू येतो. अग्नि-पॅनेलवर कॉन्कोर्समधील एक धूर-शोधक इशारा दाखवतो; धुराची कोणतीही माहिती आलेली नाही. सर्वोत्तम पहिली कृती कोणती?",
  [("Send a staff member to check that detector at once and keep OCC informed", "त्या शोधकाची तपासणी करण्यासाठी लगेच कर्मचारी पाठवा व OCC ला माहिती देत रहा", 1.0),
   ("Start a full station evacuation straight away and stop trains from stopping", "लगेच संपूर्ण स्थानक रिकामे करा आणि गाड्यांचा थांबा बंद करा", 0.4),
   ("Silence and reset the alarm from the panel to avoid causing passenger panic", "प्रवाशांमध्ये घबराट होऊ नये म्हणून पॅनेलवरून इशारा बंद करून रीसेट करा", 0.0),
   ("Announce on the PA that the alarm is a test and ask passengers to ignore it", "PA वर इशारा चाचणी असल्याचे सांगा आणि प्रवाशांना दुर्लक्ष करण्यास सांगा", 0.0)],
  lvl=3, bloom="Analyse", fam=OPS + ",SFS,EM", media="fire_alarm_tone.mp3", sme=True,
  why="A single-detector alarm is usually verified at once; confirmation or a second detector triggers evacuation. Resetting or calling it a test without checking is dangerous.",
  why_mr="एकाच शोधकाचा इशारा सहसा लगेच तपासला जातो; खात्री झाल्यास किंवा दुसरा शोधक सक्रिय झाल्यास स्थानक रिकामे केले जाते. न तपासता रीसेट करणे किंवा चाचणी म्हणणे धोकादायक आहे.",
  src="Station fire emergency SOP (to be cited)")
Q("C01-0006", "C01", "Arrange the steps for using a portable fire extinguisher.", "पोर्टेबल अग्निशामक वापरण्याचे टप्पे क्रमाने लावा.",
  [("Pull the safety pin", "सेफ्टी पिन काढा"), ("Aim the nozzle at the base of the fire", "नोजल आगीच्या मुळाकडे धरा"),
   ("Squeeze the handle", "हँडल दाबा"), ("Sweep from side to side", "एका बाजूकडून दुसऱ्या बाजूकडे फिरवा")],
  lvl=1, bloom="Remember", pool="practice", why="PASS: Pull, Aim, Squeeze, Sweep.", why_mr="PASS: पिन काढा, नेम धरा, दाबा, फिरवा.")

# ====================================================================== C02 METRO SYSTEM AWARENESS
S("C02-0001", "C02", "On a CBTC line, what keeps two trains at a safe distance from each other?",
  "CBTC मार्गावर दोन गाड्यांमध्ये सुरक्षित अंतर कशामुळे राखले जाते?",
  [("Onboard ATP enforcing the movement authority it receives", "मिळालेल्या हालचाल-अधिकाराची अंमलबजावणी करणारे ऑनबोर्ड ATP", True),
   ("The operator keeping a visual gap from the train ahead", "पुढील गाडीपासून नजरेने अंतर राखणारा चालक", False),
   ("Colour-light signals placed at fixed block sections", "निश्चित ब्लॉक विभागांवरील रंगीत दिव्यांचे सिग्नल", False),
   ("OCC releasing each block section to trains by radio", "प्रत्येक ब्लॉक विभाग रेडिओने गाड्यांना देणारे OCC", False)],
  lvl=2, bloom="Understand", why="In CBTC the zone controller sends a movement authority and the onboard ATP enforces speed and braking within it.",
  why_mr="CBTC मध्ये झोन कंट्रोलर हालचाल-अधिकार पाठवतो आणि ऑनबोर्ड ATP त्या मर्यादेत वेग व ब्रेकिंग लागू करते.")
X("C02-0002", "C02", "Match each fault with the department that should be called first.", "प्रत्येक बिघाडासाठी आधी कोणत्या विभागाला बोलवावे ते जुळवा.",
  [(("Escalator stopped with a fault code", "दोष-कोडसह एस्केलेटर बंद"), ("Electrical & Mechanical", "विद्युत व यांत्रिक")),
   (("Gate rejects every smart card", "गेट प्रत्येक स्मार्ट कार्ड नाकारते"), ("AFC", "AFC")),
   (("OHE breaker tripped in a section", "विभागातील OHE ब्रेकर ट्रिप"), ("Traction & Power Supply", "ट्रॅक्शन व वीजपुरवठा")),
   (("Section shows occupied with no train", "गाडी नसताना विभाग व्यापलेला दिसतो"), ("Signalling", "सिग्नलिंग"))],
  lvl=2, bloom="Apply")
S("C02-0003", "C02", "A platform screen door does not close at the end of dwell. Which maintenance team is normally called?",
  "थांब्याच्या शेवटी प्लॅटफॉर्म स्क्रीन दरवाजा बंद होत नाही. सामान्यतः कोणत्या देखभाल पथकाला बोलवले जाते?",
  [("Signalling, as the PSD is interlocked with train control", "सिग्नलिंग, कारण PSD ट्रेन-नियंत्रणाशी इंटरलॉक्ड असतो", True),
   ("E&M, as the doors are driven by electric motors", "E&M, कारण दरवाजे विद्युत मोटरने चालतात", False),
   ("Rolling stock, as the train doors control the PSD", "रोलिंग स्टॉक, कारण गाडीचे दरवाजे PSD नियंत्रित करतात", False),
   ("Civil, as the PSD is fixed to the platform edge", "स्थापत्य, कारण PSD प्लॅटफॉर्मच्या कडेला बसवलेला असतो", False)],
  lvl=2, bloom="Understand", pool="practice", sme=True, why="At the CoE the PSD is housed in the Signalling Lab, reflecting its interlocking with train control.",
  why_mr="CoE मध्ये PSD सिग्नलिंग लॅबमध्ये आहे, कारण तो ट्रेन-नियंत्रणाशी इंटरलॉक्ड आहे.")
M("C02-0004", "C02", "Which of these must be reported to OCC at once?", "यापैकी कोणती बाब OCC ला ताबडतोब कळवली पाहिजे?",
  [("A person seen walking on the viaduct track", "व्हायाडक्टवरील रुळावरून चालणारी व्यक्ती दिसणे", True),
   ("A platform screen door that will not close", "बंद न होणारा प्लॅटफॉर्म स्क्रीन दरवाजा", True),
   ("A passenger injured in a fall on an escalator", "एस्केलेटरवर पडून जखमी झालेला प्रवासी", True),
   ("A ticket vending machine out of receipt paper", "पावतीचा कागद संपलेले तिकीट यंत्र", False),
   ("A housekeeping contractor arriving 20 minutes late", "स्वच्छता कंत्राटदार 20 मिनिटे उशिरा येणे", False)],
  lvl=2, bloom="Understand", fam=TECH)
TF("C02-0005", "C02", "Trains on Maha Metro's Nagpur and Pune lines draw traction power from a 25 kV AC overhead line.",
   "महा मेट्रोच्या नागपूर व पुणे मार्गांवरील गाड्या 25 kV AC ओव्हरहेड लाइनमधून ट्रॅक्शन वीज घेतात.", True,
   lvl=1, bloom="Remember", pool="practice", sme=True)

# ====================================================================== C03 CUSTOMER ORIENTATION
J("C03-0001", "C03",
  "A wheelchair user wants to reach the platform, but the only lift on that side is out of service. What is the best response?",
  "व्हीलचेअर वापरकर्त्याला प्लॅटफॉर्मवर जायचे आहे, पण त्या बाजूची एकमेव लिफ्ट बंद आहे. सर्वोत्तम प्रतिसाद कोणता?",
  [("Escort the passenger to the working lift on the other side and inform the destination", "प्रवाशाला दुसऱ्या बाजूच्या चालू लिफ्टपर्यंत घेऊन जा व गंतव्य स्थानकाला कळवा", 1.0),
   ("Explain clearly where the working lift is and how to reach it from the concourse", "चालू लिफ्ट कोठे आहे व कॉन्कोर्समधून तिथे कसे जायचे ते स्पष्ट सांगा", 0.4),
   ("Arrange two staff members to help the passenger up the escalator carefully", "दोन कर्मचाऱ्यांच्या मदतीने प्रवाशाला काळजीपूर्वक एस्केलेटरने वर न्या", 0.0),
   ("Suggest boarding at the next station, where lifts work, and call that station", "लिफ्ट चालू असलेल्या पुढील स्थानकातून प्रवास सुचवा व त्या स्थानकाला कळवा", 0.3)],
  lvl=3, fam=TECH + ",COM", why="Escorting keeps the passenger safe and informing the destination ensures help on arrival. Wheelchairs on escalators are unsafe even with help.",
  why_mr="सोबत नेल्याने प्रवासी सुरक्षित राहतो व गंतव्य स्थानकाला कळवल्याने पोहोचल्यावर मदत मिळते. मदत असली तरी एस्केलेटरवर व्हीलचेअर असुरक्षित आहे.")
J("C03-0002", "C03", "A passenger says the gate deducted the fare twice from his card and is getting angry. What do you do?",
  "एक प्रवासी म्हणतो की गेटने त्याच्या कार्डमधून दोनदा भाडे कापले आणि तो संतप्त होत आहे. तुम्ही काय कराल?",
  [("Check the card history at the ticket office and correct or refund it as per rules", "तिकीट कार्यालयात कार्डचा इतिहास तपासा व नियमांनुसार दुरुस्ती किंवा परतावा करा", 1.0),
   ("Give him the complaint number and ask him to follow up with customer care", "त्याला तक्रार क्रमांक द्या व ग्राहक सेवेकडे पाठपुरावा करण्यास सांगा", 0.3),
   ("Refund the fare in cash from the counter at once to calm the situation", "परिस्थिती शांत करण्यासाठी काउंटरवरून लगेच रोख भाडे परत द्या", 0.1),
   ("Explain that gates rarely make errors and suggest he contact his bank", "गेट क्वचितच चूक करतात असे समजावून बँकेशी संपर्क करण्यास सुचवा", 0.0)],
  lvl=3, fam=OPS + ",COM,STA", why="Checking the transaction record resolves the complaint on the spot and within the rules; an unrecorded cash refund breaks revenue controls.",
  why_mr="व्यवहाराची नोंद तपासल्याने तक्रार जागीच व नियमांनुसार सुटते; नोंद न करता रोख परतावा दिल्यास महसूल नियंत्रण मोडते.")
S("C03-0003", "C03",
  "You are counting cash at the ticket office when a passenger starts complaining loudly about a 15-minute delay. What should you do first?",
  "तिकीट कार्यालयात तुम्ही रोख मोजत असताना एक प्रवासी 15 मिनिटांच्या विलंबाबद्दल मोठ्याने तक्रार करू लागतो. तुम्ही प्रथम काय कराल?",
  [("Secure the cash, then give the passenger your full attention", "रोख सुरक्षित ठेवा, मग प्रवाशाकडे पूर्ण लक्ष द्या", True),
   ("Finish counting first so that no cash error is made", "रोख चूक होऊ नये म्हणून आधी मोजणी पूर्ण करा", False),
   ("Explain that train delays are handled by OCC, not stations", "गाड्यांचा विलंब OCC हाताळते, स्थानक नाही असे सांगा", False),
   ("Hand over a complaint form and continue with your work", "तक्रार अर्ज देऊन तुमचे काम सुरू ठेवा", False)],
  lvl=2, pool="practice")
J("C03-0004", "C03", "A visually impaired passenger asks for help to reach platform 2 during peak hour. What is the best response?",
  "गर्दीच्या वेळी एक दृष्टिहीन प्रवासी प्लॅटफॉर्म 2 वर जाण्यासाठी मदत मागतो. सर्वोत्तम प्रतिसाद कोणता?",
  [("Offer your arm, guide him to the platform and tell the platform staff", "तुमचा हात देऊ करा, त्याला प्लॅटफॉर्मपर्यंत न्या व प्लॅटफॉर्म कर्मचाऱ्यांना सांगा", 1.0),
   ("Describe the route along the tactile path and the lift to use", "स्पर्श-मार्ग व वापरायची लिफ्ट यांचे मार्गवर्णन सांगा", 0.5),
   ("Ask a passenger going the same way to take him along", "त्याच दिशेने जाणाऱ्या प्रवाशाला त्याला सोबत नेण्यास सांगा", 0.2),
   ("Ask him to wait near the SCR until the peak crowd clears", "गर्दी कमी होईपर्यंत SCR जवळ थांबण्यास सांगा", 0.0)],
  lvl=2, pool="both")

# ====================================================================== C04 INTEGRITY
J("C04-0001", "C04",
  "A contractor's representative leaves an expensive gift at your desk after a bill you processed was cleared. What is the best response?",
  "तुम्ही प्रक्रिया केलेले बिल मंजूर झाल्यानंतर कंत्राटदाराचा प्रतिनिधी तुमच्या टेबलवर महागडी भेटवस्तू ठेवून जातो. सर्वोत्तम प्रतिसाद कोणता?",
  [("Return it and report it as required by the conduct rules", "आचार नियमांनुसार ती परत करा व त्याची माहिती द्या", 1.0),
   ("Keep it and declare it in your next annual property return", "ती ठेवा व पुढील वार्षिक मालमत्ता विवरणात जाहीर करा", 0.2),
   ("Hand it to the department for common use and thank him", "सामायिक वापरासाठी विभागाकडे द्या व त्याचे आभार माना", 0.3),
   ("Accept it, as the bill is already cleared and cannot be influenced", "बिल आधीच मंजूर झाल्याने प्रभाव पडत नाही, म्हणून स्वीकारा", 0.0)],
  lvl=2, why="Gifts from parties with official dealings must be declined or reported; a cleared bill does not remove the expectation of future favour.",
  why_mr="अधिकृत व्यवहार असलेल्यांकडील भेटवस्तू नाकाराव्या किंवा कळवाव्या; बिल मंजूर झाले तरी भविष्यातील उपकाराची अपेक्षा संपत नाही.",
  src="Conduct, Discipline & Appeal Rules; preventive vigilance guidelines")
S("C04-0002", "C04", "You are on a bid evaluation committee and learn that your brother-in-law's firm has bid. What should you do?",
  "तुम्ही निविदा मूल्यमापन समितीवर आहात आणि तुमच्या मेहुण्याच्या फर्मने निविदा भरली आहे हे कळते. काय करावे?",
  [("Declare the relationship in writing and step aside from the evaluation", "नाते लेखी जाहीर करा व मूल्यमापनातून बाजूला व्हा", True),
   ("Continue, but do not score that particular firm's bid yourself", "सुरू ठेवा, पण त्या फर्मच्या निविदेला स्वतः गुण देऊ नका", False),
   ("Tell the committee chair orally and continue as a member", "समिती अध्यक्षांना तोंडी सांगा व सदस्य म्हणून सुरू ठेवा", False),
   ("Ask your relative's firm to withdraw so the conflict ends", "संघर्ष संपावा म्हणून नातेवाईकाच्या फर्मला माघार घेण्यास सांगा", False)],
  lvl=3)
TF("C04-0003", "C04", "Preventive vigilance is the responsibility of the vigilance department only.", "प्रतिबंधात्मक दक्षता ही केवळ दक्षता विभागाची जबाबदारी आहे.",
   False, lvl=2, bloom="Understand", pool="practice",
   why="Every employee and manager is responsible for preventive vigilance in their own work.", why_mr="प्रत्येक कर्मचारी व व्यवस्थापक आपल्या कामात प्रतिबंधात्मक दक्षतेसाठी जबाबदार आहे.")
S("C04-0004", "C04",
  "While checking a contractor's bill, you find the claimed quantity is 8% more than what you measured at site. The engineer-in-charge says, 'Pass it; we will adjust it in the next bill.' What do you do?",
  "कंत्राटदाराचे बिल तपासताना दावा केलेले प्रमाण तुम्ही साइटवर मोजलेल्यापेक्षा 8% जास्त आढळते. प्रभारी अभियंता म्हणतात, 'मंजूर करा; पुढील बिलात समायोजित करू.' तुम्ही काय कराल?",
  [("Record the measured quantity and put up a note on the difference", "मोजलेले प्रमाण नोंदवा व फरकाबाबत टिप्पणी सादर करा", True),
   ("Pass it as told, since the excess will be recovered later", "सांगितल्याप्रमाणे मंजूर करा, कारण जादा रक्कम नंतर वसूल होईल", False),
   ("Pass it, but keep a personal record in case of an audit", "मंजूर करा, पण लेखापरीक्षेसाठी वैयक्तिक नोंद ठेवा", False),
   ("Return the bill to the contractor without any comment", "कोणतीही टिप्पणी न करता बिल कंत्राटदाराला परत करा", False)],
  lvl=3, bloom="Analyse", fam=TECH + ",FA,PCS")
S("C04-0005", "C04", "Vigilance Awareness Week is observed every year in the week that includes which date?",
  "दक्षता जागरूकता सप्ताह दरवर्षी कोणत्या तारखेचा समावेश असलेल्या आठवड्यात पाळला जातो?",
  [("31 October", "31 ऑक्टोबर", True), ("15 August", "15 ऑगस्ट", False), ("2 October", "2 ऑक्टोबर", False), ("26 November", "26 नोव्हेंबर", False)],
  lvl=1, bloom="Remember", pool="practice", why="It is observed in the week of Sardar Vallabhbhai Patel's birth anniversary, 31 October.",
  why_mr="हा सरदार वल्लभभाई पटेल यांच्या जयंतीच्या (31 ऑक्टोबर) आठवड्यात पाळला जातो.")

# ====================================================================== C05 DIGITAL LITERACY & CYBER HYGIENE
S("C05-0001", "C05",
  "An email that looks like it is from IT asks you to confirm your ERP password through a link 'before your account is locked today'. What should you do?",
  "IT कडून आल्यासारखा ई-मेल 'आज खाते लॉक होण्यापूर्वी' लिंकद्वारे ERP पासवर्ड पुष्टी करण्यास सांगतो. तुम्ही काय कराल?",
  [("Do not click; report it to IT as suspected phishing", "क्लिक करू नका; संशयित फिशिंग म्हणून IT ला कळवा", True),
   ("Open the link on your mobile, not on the office PC", "लिंक कार्यालयीन संगणकाऐवजी मोबाईलवर उघडा", False),
   ("Call the phone number in the email to check it first", "आधी ई-मेलमधील फोन क्रमांकावर फोन करून खात्री करा", False),
   ("Reply asking the sender to confirm their identity", "उत्तर पाठवून पाठवणाऱ्याची ओळख निश्चित करण्यास सांगा", False)],
  lvl=2, why="Urgency plus a password request is a classic phishing sign. Contact details inside the email belong to the attacker too.",
  why_mr="घाई व पासवर्डची मागणी हे फिशिंगचे ठळक लक्षण आहे. ई-मेलमधील संपर्क तपशीलही हल्लेखोराचेच असतात.", src="IT security policy")
M("C05-0002", "C05", "Which of these are good password practices?", "यापैकी पासवर्डच्या चांगल्या पद्धती कोणत्या?",
  [("Use a long passphrase of 12 or more characters", "12 किंवा अधिक अक्षरांचा लांब पासफ्रेज वापरा", True),
   ("Turn on multi-factor authentication where offered", "उपलब्ध असेल तिथे बहु-घटक प्रमाणीकरण सुरू करा", True),
   ("Use a different password for each system", "प्रत्येक प्रणालीसाठी वेगळा पासवर्ड वापरा", True),
   ("Change one digit each month to keep it memorable", "लक्षात राहावा म्हणून दर महिन्याला एक अंक बदला", False),
   ("Save it in a note file on the office desktop", "कार्यालयीन डेस्कटॉपवरील नोट फाइलमध्ये साठवा", False)],
  lvl=2, bloom="Understand")
J("C05-0003", "C05",
  "A colleague asks for your ERP login so he can finish urgent entries while you are on leave next week. What is the best response?",
  "पुढील आठवड्यात तुम्ही रजेवर असताना तातडीच्या नोंदी पूर्ण करण्यासाठी सहकारी तुमचा ERP लॉगिन मागतो. सर्वोत्तम प्रतिसाद कोणता?",
  [("Decline, and ask the supervisor to get him temporary access through IT", "नकार द्या व पर्यवेक्षकांना IT मार्फत त्याला तात्पुरता प्रवेश मिळवून देण्यास सांगा", 1.0),
   ("Log in remotely from home yourself and do the entries for him", "घरून स्वतः दूरस्थपणे लॉगिन करून त्याच्यासाठी नोंदी करा", 0.4),
   ("Share it, and change the password as soon as you return", "शेअर करा व परत आल्यावर लगेच पासवर्ड बदला", 0.1),
   ("Share it, because the entries are for official work only", "शेअर करा, कारण नोंदी फक्त अधिकृत कामासाठीच आहेत", 0.0)],
  lvl=2)
S("C05-0004", "C05", "You find a USB pen drive labelled 'Salary revision 2027' in the car park. What should you do?",
  "पार्किंगमध्ये 'वेतन सुधारणा 2027' असे लेबल असलेला USB पेन ड्राइव्ह सापडतो. तुम्ही काय कराल?",
  [("Hand it to IT without plugging it into any computer", "कोणत्याही संगणकाला न लावता IT कडे जमा करा", True),
   ("Plug it into an office PC that has antivirus to find the owner", "मालक शोधण्यासाठी अँटीव्हायरस असलेल्या कार्यालयीन संगणकाला लावा", False),
   ("Give it to HR, since it seems to contain salary data", "वेतन माहिती असल्यासारखे दिसते म्हणून HR कडे द्या", False),
   ("Check it on a home laptop so office systems stay safe", "कार्यालयीन प्रणाली सुरक्षित राहाव्यात म्हणून घरच्या लॅपटॉपवर तपासा", False)],
  lvl=2, pool="practice", why="Tempting labels are a known trick to plant malware; only IT should examine unknown devices.",
  why_mr="आकर्षक लेबल लावून मालवेअर पसरवणे ही ज्ञात युक्ती आहे; अनोळखी उपकरण फक्त IT नेच तपासावे.")

# ====================================================================== C06 COMMUNICATION
S("C06-0001", "C06",
  "OCC tells you by radio: 'Train 112, proceed at caution to platform 2 and stop short of the obstruction.' What is the correct reply?",
  "OCC रेडिओवर सांगते: 'ट्रेन 112, सावधगिरीने प्लॅटफॉर्म 2 कडे जा आणि अडथळ्याच्या अलीकडे थांबा.' योग्य उत्तर कोणते?",
  [("Repeat the instruction back in full and wait for 'correct'", "संपूर्ण सूचना पुन्हा उच्चारा आणि 'बरोबर' ची वाट पहा", True),
   ("Say 'Roger, OCC' and start moving at caution speed", "'रॉजर, OCC' म्हणा आणि सावध वेगाने निघा", False),
   ("Give your train number, acknowledge, and act on the instruction", "तुमचा ट्रेन क्रमांक सांगा, पोच द्या आणि सूचनेनुसार कृती करा", False),
   ("Ask OCC to send the same instruction as a message", "हीच सूचना संदेशाद्वारे पाठवण्यास OCC ला सांगा", False)],
  lvl=2, fam=OPS + ",RS,STA,TPS", why="Repeat-back of safety-critical instructions confirms the exact message before acting.",
  why_mr="सुरक्षा-महत्त्वाच्या सूचना पुन्हा उच्चारल्याने कृतीपूर्वी नेमका संदेश निश्चित होतो.")
A("C06-0002", "C06",
  "Record a public announcement (in the language you selected) telling passengers that the next train is delayed by about 10 minutes due to a technical problem.",
  "तांत्रिक अडचणीमुळे पुढील गाडी सुमारे 10 मिनिटे उशिरा असल्याची प्रवाशांना माहिती देणारी उद्घोषणा (निवडलेल्या भाषेत) रेकॉर्ड करा.",
  "1 mark each: polite opening/apology; reason stated simply; expected delay given; advice or alternative offered; clear, calm diction at a steady pace.",
  "प्रत्येकी 1 गुण: नम्र सुरुवात/दिलगिरी; कारण सोप्या शब्दांत; अपेक्षित विलंब; सल्ला किंवा पर्याय; स्पष्ट, शांत व स्थिर उच्चार.",
  marks=5, lvl=3, fam=OPS + ",COM", pool="both")
W("C06-0003", "C06",
  "Write a short incident note (5–8 lines) to your superior about a passenger who slipped on a wet floor near the station entry. The passenger was not seriously hurt.",
  "स्थानक प्रवेशाजवळ ओल्या फरशीवर घसरलेल्या प्रवाशाबद्दल वरिष्ठांना छोटी घटना-नोंद (5–8 ओळी) लिहा. प्रवाशाला गंभीर इजा झाली नाही.",
  "1 mark each: time and exact location; what happened; action taken (first aid, area cordoned, cleaning); witnesses/CCTV reference; recommendation to prevent recurrence.",
  "प्रत्येकी 1 गुण: वेळ व अचूक ठिकाण; काय घडले; केलेली कृती (प्रथमोपचार, जागा बंदिस्त, साफसफाई); साक्षीदार/CCTV संदर्भ; पुनरावृत्ती टाळण्याची शिफारस.",
  marks=5, lvl=3)
S("C06-0004", "C06", "Trains are delayed by about 10 minutes. Which platform announcement is best?",
  "गाड्यांना सुमारे 10 मिनिटांचा विलंब आहे. प्लॅटफॉर्मवरील कोणती उद्घोषणा सर्वोत्तम?",
  [("'Due to a technical problem, the next train will arrive in about 10 minutes. We regret the delay.'",
    "'तांत्रिक अडचणीमुळे पुढील गाडी सुमारे 10 मिनिटांत येईल. विलंबाबद्दल आम्ही दिलगीर आहोत.'", True),
   ("'Trains are running late. Please wait on the platform and do not crowd near the edge.'",
    "'गाड्या उशिराने धावत आहेत. कृपया प्लॅटफॉर्मवर थांबा व कडेजवळ गर्दी करू नका.'", False),
   ("'Due to a signalling failure between stations, services are disrupted until further notice.'",
    "'स्थानकांदरम्यान सिग्नलिंग बिघाडामुळे पुढील सूचनेपर्यंत सेवा विस्कळीत आहे.'", False),
   ("'The next train is delayed. For details, please contact the customer care desk.'",
    "'पुढील गाडी उशिरा आहे. तपशिलासाठी कृपया ग्राहक सेवा कक्षाशी संपर्क साधा.'", False)],
  lvl=2, pool="practice", fam=OPS + ",COM",
  why="A good announcement gives a simple reason, the expected wait and an apology; 'until further notice' and 'contact the desk' leave passengers without the information they need.",
  why_mr="चांगल्या उद्घोषणेत सोपे कारण, अपेक्षित प्रतीक्षा व दिलगिरी असते; 'पुढील सूचनेपर्यंत' किंवा 'कक्षाशी संपर्क' यामुळे प्रवाशांना आवश्यक माहिती मिळत नाही.")

# ====================================================================== BEHAVIOURAL (SJT - every option is something people really do)
J("B01-0001", "B01",
  "The night maintenance block is ending. Another department's team is still clearing material from the track; your team has finished. What do you do?",
  "रात्रीचा देखभाल ब्लॉक संपत आला आहे. दुसऱ्या विभागाची टीम अजून रुळावरील साहित्य हटवत आहे; तुमचे काम पूर्ण झाले आहे. तुम्ही काय कराल?",
  [("Offer your team's help, agree it with their in-charge and update the controller", "तुमच्या टीमची मदत देऊ करा, त्यांच्या प्रभारीशी ठरवा व नियंत्रकाला माहिती द्या", 1.0),
   ("Tell the controller your part is clear and that the other team is still working", "तुमचा भाग मोकळा असून दुसरी टीम काम करत आहे असे नियंत्रकाला सांगा", 0.5),
   ("Sign off your part of the block and leave, as their work is their responsibility", "तुमच्या भागाचा ब्लॉक बंद करून निघा, कारण त्यांचे काम त्यांची जबाबदारी आहे", 0.1),
   ("Wait on site without interfering until they finish, then leave together", "हस्तक्षेप न करता ते पूर्ण करेपर्यंत थांबा, मग एकत्र निघा", 0.3)],
  lvl=3, fam=TECH)
J("B01-0002", "B01", "A colleague on another shift regularly leaves handover notes incomplete. What is the best approach?",
  "दुसऱ्या पाळीतील सहकारी नियमितपणे हस्तांतरण नोंदी अपूर्ण ठेवतो. सर्वोत्तम पद्धत कोणती?",
  [("Talk to him, agree a handover checklist, and escalate if it continues", "त्याच्याशी बोला, हस्तांतरण-यादी ठरवा व तरीही सुरू राहिल्यास वरिष्ठांकडे न्या", 1.0),
   ("Fill in the missing details yourself each time so that work is not affected", "काम अडू नये म्हणून प्रत्येक वेळी गहाळ तपशील स्वतः भरा", 0.3),
   ("Report the incomplete notes to the supervisor in writing straight away", "अपूर्ण नोंदींची लेखी तक्रार लगेच पर्यवेक्षकाकडे करा", 0.4),
   ("Leave your own handover notes equally brief so the shifts stay consistent", "पाळ्यांमध्ये सुसंगती राहावी म्हणून तुमच्याही नोंदी तेवढ्याच त्रोटक ठेवा", 0.0)],
  lvl=2, pool="practice")
J("B02-0001", "B02",
  "A fault indication appears on equipment you maintain, but the manual's troubleshooting steps do not match what you see. What do you do?",
  "तुम्ही देखभाल करत असलेल्या उपकरणावर दोष-सूचना येते, पण मॅन्युअलमधील तपासणी टप्पे दिसणाऱ्या स्थितीशी जुळत नाहीत. तुम्ही काय कराल?",
  [("Make it safe, record what you observe and escalate to the senior engineer or OEM", "उपकरण सुरक्षित करा, निरीक्षणे नोंदवा व वरिष्ठ अभियंता किंवा OEM कडे पाठवा", 1.0),
   ("Reset the equipment and keep it under observation for the rest of the shift", "उपकरण रीसेट करा व उर्वरित पाळीत त्यावर लक्ष ठेवा", 0.3),
   ("Try the closest matching procedure in the manual and note any differences", "मॅन्युअलमधील सर्वात जवळची कार्यपद्धती वापरा व फरक नोंदवा", 0.4),
   ("Hand it over to the next shift with a note, as it needs more time", "अधिक वेळ लागणार म्हणून टिप्पणीसह पुढच्या पाळीकडे सोपवा", 0.1)],
  lvl=3, bloom="Analyse", fam=TECH)
J("B02-0002", "B02", "Your section has two urgent requests and capacity for only one before the deadline. What is the best first step?",
  "तुमच्या विभागाकडे दोन तातडीच्या विनंत्या आहेत पण मुदतीपूर्वी एकच पूर्ण करता येईल. सर्वोत्तम पहिला टप्पा कोणता?",
  [("Compare the safety and service impact, decide, and inform both", "सुरक्षा व सेवेवरील परिणामांची तुलना करा, निर्णय घ्या व दोघांना कळवा", 1.0),
   ("Take up the request from the more senior officer and inform the other", "अधिक वरिष्ठ अधिकाऱ्याची विनंती घ्या व दुसऱ्याला कळवा", 0.3),
   ("Start both in parallel and complete whichever is ready first", "दोन्ही एकाच वेळी सुरू करा व जे आधी तयार होईल ते पूर्ण करा", 0.1),
   ("Refer both to your HoD and ask which one should be done first", "दोन्ही HoD कडे पाठवा व आधी कोणते करायचे ते विचारा", 0.4)],
  lvl=3, bloom="Analyse", pool="practice")
J("B03-0001", "B03",
  "During peak hour a train is cancelled, a crowd builds up on the platform and some passengers start shouting at you. What do you do?",
  "गर्दीच्या वेळी एक गाडी रद्द होते, प्लॅटफॉर्मवर गर्दी वाढते आणि काही प्रवासी तुमच्यावर ओरडू लागतात. तुम्ही काय कराल?",
  [("Stay calm, inform clearly, follow crowd-control steps and call the SCR", "शांत रहा, स्पष्ट माहिती द्या, गर्दी-नियंत्रण टप्पे पाळा व SCR ला बोलवा", 1.0),
   ("Move away from the angry group and manage the crowd from the other end", "संतप्त गटापासून दूर जा व दुसऱ्या टोकावरून गर्दी हाताळा", 0.3),
   ("Promise them the next train will arrive in two or three minutes", "पुढची गाडी दोन-तीन मिनिटांत येईल असे आश्वासन द्या", 0.1),
   ("Ask security staff to remove the passengers who are shouting", "ओरडणाऱ्या प्रवाशांना बाहेर काढण्यास सुरक्षा कर्मचाऱ्यांना सांगा", 0.2)],
  lvl=3, fam=OPS + ",SFS")
J("B03-0002", "B03", "During a simulator exercise, an unexpected combination of faults appears. What is the best response?",
  "सिम्युलेटर सरावात अनपेक्षित दोषांचे संयोजन दिसते. सर्वोत्तम प्रतिसाद कोणता?",
  [("Pause, handle the most safety-critical fault first, and inform OCC", "थांबा, आधी सर्वात सुरक्षा-महत्त्वाचा दोष हाताळा व OCC ला कळवा", 1.0),
   ("Deal with the faults in the order in which they appeared on the screen", "दोष स्क्रीनवर ज्या क्रमाने दिसले त्याच क्रमाने हाताळा", 0.3),
   ("Act quickly from experience, then check the procedure afterwards", "अनुभवाने पटकन कृती करा, नंतर कार्यपद्धती तपासा", 0.1),
   ("Stop and ask the instructor how to proceed with two faults together", "थांबा व दोन दोष एकत्र कसे हाताळायचे ते प्रशिक्षकाला विचारा", 0.2)],
  lvl=3, pool="practice", fam=TECH)
J("B04-0001", "B04", "You realise that a measurement you recorded yesterday in the maintenance log was wrong. What do you do?",
  "काल देखभाल नोंदवहीत नोंदवलेले मोजमाप चुकीचे होते हे तुमच्या लक्षात येते. तुम्ही काय कराल?",
  [("Correct it by the record-correction procedure and tell your supervisor", "नोंद-दुरुस्ती कार्यपद्धतीनुसार दुरुस्त करा व पर्यवेक्षकाला सांगा", 1.0),
   ("Re-measure at the next scheduled inspection and record the new value then", "पुढील नियोजित तपासणीत पुन्हा मोजा व तेव्हा नवे मूल्य नोंदवा", 0.3),
   ("Correct the figure neatly in the log so the record looks right", "नोंद बरोबर दिसावी म्हणून वहीत आकडा व्यवस्थित दुरुस्त करा", 0.1),
   ("Leave it, because the difference is within the usual tolerance", "फरक नेहमीच्या सहनशीलतेत असल्याने तसेच राहू द्या", 0.0)],
  lvl=2)
J("B04-0002", "B04", "You promised a stakeholder a report by Friday, but you now see it will be a day late. What is best?",
  "तुम्ही एका भागधारकाला शुक्रवारपर्यंत अहवाल देण्याचे वचन दिले होते, पण आता तो एक दिवस उशिरा होईल. काय सर्वोत्तम?",
  [("Tell them now, with the reason and a firm new date", "त्यांना आताच कारण व निश्चित नवी तारीख सांगा", 1.0),
   ("Send the finished sections on Friday and the rest later", "पूर्ण झालेले भाग शुक्रवारी पाठवा व उरलेले नंतर", 0.5),
   ("Work late on Thursday to finish it, even if quality drops", "गुणवत्ता कमी झाली तरी गुरुवारी उशिरापर्यंत काम करून पूर्ण करा", 0.2),
   ("Send it on Monday; one day will not make a difference", "सोमवारी पाठवा; एका दिवसाने फरक पडणार नाही", 0.0)],
  lvl=2, pool="practice")
J("B05-0001", "B05", "A new technician in your team keeps making mistakes in a routine task. What is the best approach?",
  "तुमच्या टीममधील नवीन तंत्रज्ञ एका नेहमीच्या कामात वारंवार चुका करतो. सर्वोत्तम पद्धत कोणती?",
  [("Show the task, watch him do it, give specific feedback and check again in a week", "काम करून दाखवा, त्याला करताना पहा, नेमका अभिप्राय द्या व आठवड्याने पुन्हा तपासा", 1.0),
   ("Nominate him for the next CoE course on the task and review afterwards", "त्या कामावरील पुढील CoE अभ्यासक्रमासाठी नामनिर्देशन करा व नंतर आढावा घ्या", 0.5),
   ("Pair him permanently with an experienced technician for that task", "त्या कामासाठी त्याला कायमस्वरूपी अनुभवी तंत्रज्ञासोबत जोडा", 0.3),
   ("Give the task to someone else until he gains more experience", "त्याला अधिक अनुभव येईपर्यंत ते काम दुसऱ्याला द्या", 0.1)],
  lvl=3)
J("B05-0002", "B05", "Two experienced staff in your team are reluctant to share their troubleshooting knowledge with juniors. What do you do?",
  "तुमच्या टीममधील दोन अनुभवी कर्मचारी आपले दोष-निवारण ज्ञान कनिष्ठांना देण्यास नाखूष आहेत. तुम्ही काय कराल?",
  [("Recognise their expertise and make mentoring part of their role", "त्यांच्या कौशल्याची दखल घ्या व मार्गदर्शन त्यांच्या भूमिकेचा भाग करा", 1.0),
   ("Ask them to write their methods down so juniors can read them", "कनिष्ठांना वाचता यावे म्हणून त्यांच्या पद्धती लिहून ठेवण्यास सांगा", 0.4),
   ("Arrange outside training for the juniors instead", "त्याऐवजी कनिष्ठांसाठी बाहेरील प्रशिक्षणाची व्यवस्था करा", 0.2),
   ("Raise it in their annual appraisal as a development point", "वार्षिक मूल्यांकनात विकासाचा मुद्दा म्हणून नोंदवा", 0.1)],
  lvl=3, pool="practice")
J("B06-0001", "B06", "You must plan a week of preventive maintenance while two of your staff are on leave. What is the best first step?",
  "तुमचे दोन कर्मचारी रजेवर असताना एका आठवड्याच्या प्रतिबंधात्मक देखभालीचे नियोजन करायचे आहे. सर्वोत्तम पहिला टप्पा कोणता?",
  [("Rank tasks by safety and due date, fit them to staff and blocks, agree deferrals", "सुरक्षा व देय तारखेनुसार कामांचा क्रम लावा, कर्मचारी व ब्लॉकशी जुळवा, पुढे ढकलण्यास मान्यता घ्या", 1.0),
   ("Ask for overtime so the remaining staff can cover the full week's schedule", "उर्वरित कर्मचाऱ्यांना संपूर्ण आठवड्याचे वेळापत्रक पूर्ण करता यावे म्हणून जादा वेळ मागा", 0.3),
   ("Do the tasks that are quickest to finish first, to show progress", "प्रगती दिसावी म्हणून सर्वात लवकर होणारी कामे आधी करा", 0.1),
   ("Postpone the whole week's schedule until both staff return", "दोघे परत येईपर्यंत संपूर्ण आठवड्याचे वेळापत्रक पुढे ढकला", 0.0)],
  lvl=3, fam=TECH)
J("B06-0002", "B06", "A file needs inputs from three sections within ten days. How do you organise it?",
  "एका फाइलसाठी दहा दिवसांत तीन विभागांचे इनपुट हवे आहेत. तुम्ही ते कसे आयोजित कराल?",
  [("Send each section specific questions and a due date, then track replies", "प्रत्येक विभागाला नेमके प्रश्न व देय तारीख पाठवा, मग उत्तरांचा मागोवा घ्या", 1.0),
   ("Send one general email to all three asking for inputs within ten days", "दहा दिवसांत इनपुट मागणारा एकच सर्वसाधारण ई-मेल तिघांना पाठवा", 0.4),
   ("Draft the inputs yourself and ask each section only to confirm them", "इनपुट स्वतः तयार करा व प्रत्येक विभागाला फक्त पुष्टी करण्यास सांगा", 0.3),
   ("Call a meeting of all three sections on the ninth day", "नवव्या दिवशी तिन्ही विभागांची बैठक बोलवा", 0.1)],
  lvl=2, pool="practice")
J("B07-0001", "B07", "A residents' group complains about night-time noise from viaduct maintenance near their building. What is the best response?",
  "रहिवासी गट त्यांच्या इमारतीजवळील व्हायाडक्ट देखभालीच्या रात्रीच्या आवाजाबद्दल तक्रार करतो. सर्वोत्तम प्रतिसाद कोणता?",
  [("Meet them, explain why night work is needed, share the schedule, cut noise", "त्यांना भेटा, रात्रीचे काम का आवश्यक ते सांगा, वेळापत्रक द्या, आवाज कमी करा", 1.0),
   ("Send a written reply explaining that metro maintenance is permitted at night", "मेट्रो देखभालीला रात्री परवानगी असल्याचे लेखी उत्तर पाठवा", 0.3),
   ("Shift the noisiest work to daytime blocks, even if it affects service", "सेवेवर परिणाम झाला तरी सर्वात आवाजाचे काम दिवसाच्या ब्लॉकमध्ये हलवा", 0.2),
   ("Refer the group to the local municipal office for a decision", "निर्णयासाठी गटाला स्थानिक महानगरपालिका कार्यालयाकडे पाठवा", 0.0)],
  lvl=3)
J("B07-0002", "B07", "A partner agency is delaying a permission your project needs next month. What is the best approach?",
  "भागीदार संस्था तुमच्या प्रकल्पाला पुढील महिन्यात लागणारी परवानगी देण्यास उशीर करत आहे. सर्वोत्तम पद्धत कोणती?",
  [("Understand their concerns, supply what they need, agree a timeline", "त्यांच्या अडचणी समजून घ्या, आवश्यक माहिती द्या, वेळापत्रक ठरवा", 1.0),
   ("Escalate it at once to the top management of both organisations", "दोन्ही संस्थांच्या सर्वोच्च व्यवस्थापनाकडे लगेच पाठवा", 0.3),
   ("Send weekly reminder letters until the permission is issued", "परवानगी मिळेपर्यंत दर आठवड्याला स्मरणपत्रे पाठवा", 0.2),
   ("Start the preparatory work and regularise it after permission comes", "पूर्वतयारीचे काम सुरू करा व परवानगी आल्यावर नियमित करा", 0.0)],
  lvl=3, pool="practice")

# ====================================================================== D01 GENERAL & SUBSIDIARY RULES
S("D01-0001", "D01",
  "During a signalling failure your train is held at a stop signal. OCC has not answered the train radio for two minutes and the line ahead looks clear. What should you do?",
  "सिग्नलिंग बिघाडात तुमची गाडी थांबा-सिग्नलवर उभी आहे. दोन मिनिटांपासून OCC ट्रेन रेडिओला उत्तर देत नाही आणि पुढील मार्ग मोकळा दिसतो. तुम्ही काय करावे?",
  [("Stay stationary and contact OCC by the other means laid down", "गाडी थांबवून ठेवा व नियमात दिलेल्या इतर मार्गाने OCC शी संपर्क करा", True),
   ("Move at restricted speed to the next signal, as the line is clear", "मार्ग मोकळा असल्याने प्रतिबंधित वेगाने पुढील सिग्नलपर्यंत जा", False),
   ("Ask the Station Controller behind you for permission to proceed", "मागील स्थानकाच्या स्टेशन कंट्रोलरकडून पुढे जाण्याची परवानगी घ्या", False),
   ("Switch to restricted manual mode, since ATP will limit the speed", "प्रतिबंधित मॅन्युअल मोडमध्ये जा, कारण ATP वेग मर्यादित ठेवेल", False)],
  lvl=3, bloom="Apply", fam="TO,OCC,SC", sme=True, src="General & Subsidiary Rules – passing signals at danger (to be cited)",
  why="A stop signal may be passed only on proper authority from the controller. A clear-looking line, another station's permission or a mode change is not authority.",
  why_mr="थांबा-सिग्नल फक्त नियंत्रकाच्या योग्य अधिकारानेच ओलांडता येतो. मोकळा दिसणारा मार्ग, दुसऱ्या स्थानकाची परवानगी किंवा मोड बदल हा अधिकार नाही.")
S("D01-0002", "D01",
  "A 30 km/h temporary speed restriction applies from km 12.400 to km 12.600. Your train is 130 m long. Where must the front of the train be before you accelerate?",
  "km 12.400 ते km 12.600 दरम्यान 30 km/h तात्पुरती वेग-मर्यादा आहे. तुमची गाडी 130 मीटर लांब आहे. वेग वाढवण्यापूर्वी गाडीचे पुढचे टोक कोठे असले पाहिजे?",
  [("At km 12.730", "km 12.730 वर", True), ("At km 12.600", "km 12.600 वर", False),
   ("At km 12.530", "km 12.530 वर", False), ("At km 12.650", "km 12.650 वर", False)],
  lvl=3, bloom="Apply", fam="TO,OCC", sme=True,
  why="The whole train must clear the restriction: 12.600 + 0.130 = 12.730.", why_mr="संपूर्ण गाडीने मर्यादा पार केली पाहिजे: 12.600 + 0.130 = 12.730.")
S("D01-0003", "D01", "A maintenance block is in force in a section. Who may allow a train to enter that section?",
  "एका विभागात देखभाल ब्लॉक लागू आहे. त्या विभागात गाडीला प्रवेश करू देण्याची परवानगी कोण देऊ शकतो?",
  [("The traffic controller, after the block is cancelled", "ब्लॉक रद्द झाल्यानंतर वाहतूक नियंत्रक", True),
   ("The engineer-in-charge, once his work is finished", "काम संपल्यावर प्रभारी अभियंता", False),
   ("The Station Controller of the next station ahead", "पुढील स्थानकाचा स्टेशन कंट्रोलर", False),
   ("The train operator, after a visual check of the line", "मार्गाची नजरेने तपासणी केल्यावर चालक", False)],
  lvl=2, bloom="Understand", fam="TO,SC,OCC,RS,STA,TPS,CT", pool="practice", sme=True)

# ====================================================================== D02 TRAIN DRIVING (CBTC)
S("D02-0001", "D02", "Which statement about CBTC automatic train operation (ATO) mode is correct?", "CBTC च्या ऑटोमॅटिक ट्रेन ऑपरेशन (ATO) मोडबद्दल कोणते विधान बरोबर आहे?",
  [("The train runs and stops automatically under ATP, while the operator monitors", "ATP अंतर्गत गाडी आपोआप धावते व थांबते, तर चालक निरीक्षण करतो", True),
   ("The train runs automatically, but the operator may exceed limits if late", "गाडी आपोआप धावते, पण उशीर झाल्यास चालक मर्यादा ओलांडू शकतो", False),
   ("The operator controls the speed, and ATO only shows an advisory speed", "चालक वेग नियंत्रित करतो आणि ATO फक्त सुचवलेला वेग दाखवते", False),
   ("ATP protection is relaxed between stations to give a smoother ride", "प्रवास सुरळीत व्हावा म्हणून स्थानकांदरम्यान ATP संरक्षण शिथिल होते", False)],
  lvl=2, bloom="Understand", fam="TO,OCC,RS")
J("D02-0002", "D02",
  "At departure, the DMI shows one door not closed and locked. Platform staff say the door looks closed. What should you do?",
  "निघताना DMI वर एक दरवाजा 'बंद व लॉक नाही' असे दिसते. प्लॅटफॉर्म कर्मचारी म्हणतात दरवाजा बंद दिसतो. तुम्ही काय कराल?",
  [("Follow the door-fault procedure: inform OCC, check and isolate as per rules", "दरवाजा-बिघाड कार्यपद्धती पाळा: OCC ला कळवा, तपासा व नियमानुसार वेगळा करा", 1.0),
   ("Re-open and close all doors once more before telling anyone", "कोणालाही सांगण्यापूर्वी सर्व दरवाजे पुन्हा एकदा उघडून बंद करा", 0.4),
   ("Detrain all passengers at this platform and ask for a relief train", "या प्लॅटफॉर्मवर सर्व प्रवासी उतरवा व पर्यायी गाडी मागवा", 0.2),
   ("Use the door bypass to depart, as platform staff confirm it is shut", "प्लॅटफॉर्म कर्मचाऱ्यांनी खात्री दिल्याने डोअर-बायपास वापरून निघा", 0.0)],
  lvl=3, fam="TO,OCC,RS", sme=True, src="Train operator's rule book – door faults (to be cited)")
TF("D02-0003", "D02", "In ATO mode the train operator must still watch the line ahead and the platform.", "ATO मोडमध्येही चालकाने पुढील मार्ग व प्लॅटफॉर्मवर लक्ष ठेवलेच पाहिजे.",
   True, lvl=1, bloom="Remember", fam="TO,OCC", pool="practice")
S("D02-0004", "D02", "What does restricted manual (RM) driving mode generally mean?", "रिस्ट्रिक्टेड मॅन्युअल (RM) चालन मोड म्हणजे सामान्यतः काय?",
  [("Manual driving with a low capped speed and limited protection", "कमी कमाल वेग व मर्यादित संरक्षणासह हाताने चालवणे", True),
   ("Manual driving at line speed, with full ATP supervision", "पूर्ण ATP देखरेखीखाली मार्ग-वेगाने हाताने चालवणे", False),
   ("Automatic driving with the doors controlled by the operator", "चालक दरवाजे नियंत्रित करतो अशा प्रकारचे स्वयंचलित चालन", False),
   ("A depot-only mode used for coupling and washing trains", "जोडणी व धुलाईसाठी फक्त डेपोमधील मोड", False)],
  lvl=3, bloom="Understand", fam="TO,OCC,RS", sme=True)

# ====================================================================== D03 STATION OPERATIONS
S("D03-0001", "D03",
  "At station opening, the fire alarm panel shows a 'fault' (not an alarm) on one smoke-detector loop. What should the Station Controller do?",
  "स्थानक उघडताना अग्नि-इशारा पॅनेलवर एका धूर-शोधक लूपवर 'दोष' (इशारा नव्हे) दिसतो. स्टेशन कंट्रोलरने काय करावे?",
  [("Record it, inform E&M and OCC, and open with extra watch on that area", "नोंद करा, E&M व OCC ला कळवा आणि त्या भागावर जादा लक्ष ठेवून उघडा", True),
   ("Keep the station closed until the loop fault has been repaired", "लूपमधील दोष दुरुस्त होईपर्यंत स्थानक बंद ठेवा", False),
   ("Silence and reset the panel, and open if the fault clears", "पॅनेल शांत करून रीसेट करा आणि दोष गेल्यास उघडा", False),
   ("Open normally, since a fault is not the same as a fire alarm", "सामान्यपणे उघडा, कारण दोष म्हणजे अग्नि-इशारा नव्हे", False)],
  lvl=3, bloom="Analyse", fam="SC,OCC,SFS,EM", sme=True,
  why="A loop fault means part of the station may not detect fire. The defect must be logged and repaired, and the area watched meanwhile.",
  why_mr="लूप-दोष म्हणजे स्थानकाच्या काही भागात आग ओळखली जाणार नाही. दोष नोंदवून दुरुस्त केला पाहिजे व तोपर्यंत त्या भागावर लक्ष ठेवले पाहिजे.")
M("D03-0002", "D03", "Which of these belong to the station opening checks?", "यापैकी कोणत्या तपासण्या स्थानक उघडण्याच्या तपासण्यांत येतात?",
  [("Platforms and track area clear of obstructions", "प्लॅटफॉर्म व रुळाचा भाग अडथळामुक्त", True), ("Fire alarm panel healthy", "अग्नि-इशारा पॅनेल सुस्थितीत", True),
   ("AFC gates and ticket machines in service", "AFC गेट व तिकीट यंत्रे सेवेत", True), ("PA and passenger displays working", "PA व प्रवासी प्रदर्शक कार्यरत", True),
   ("Monthly escalator brake test completed", "एस्केलेटरची मासिक ब्रेक-चाचणी पूर्ण", False),
   ("Previous month's revenue reconciled with AFC central system", "मागील महिन्याचा महसूल AFC केंद्रीय प्रणालीशी जुळवलेला", False)],
  lvl=2, bloom="Remember", fam="SC")
S("D03-0003", "D03", "What does pressing the Emergency Stop Plunger (ESP) on a platform do?", "प्लॅटफॉर्मवरील इमर्जन्सी स्टॉप प्लंजर (ESP) दाबल्यावर काय होते?",
  [("Stops trains entering or leaving that platform", "त्या प्लॅटफॉर्मवर येणाऱ्या किंवा जाणाऱ्या गाड्या थांबतात", True),
   ("Switches off traction power on the whole line", "संपूर्ण मार्गावरील ट्रॅक्शन वीज बंद होते", False),
   ("Opens all platform screen doors at once", "सर्व प्लॅटफॉर्म स्क्रीन दरवाजे एकदम उघडतात", False),
   ("Stops the escalators serving that platform", "त्या प्लॅटफॉर्मचे एस्केलेटर थांबतात", False)],
  lvl=2, bloom="Remember", fam="SC,TO,OCC", pool="practice", sme=True)
S("D03-0004", "D03", "This sound plays on the platform and in the train. What does it tell passengers?", "हा आवाज प्लॅटफॉर्मवर व गाडीत वाजतो. तो प्रवाशांना काय सांगतो?",
  [("Doors are about to close", "दरवाजे बंद होणार आहेत", True), ("The train will not stop here", "गाडी इथे थांबणार नाही", False),
   ("Evacuate the station now", "आता स्थानक रिकामे करा", False), ("Next train is the last one", "पुढची गाडी शेवटची आहे", False)],
  lvl=1, bloom="Remember", fam="SC,TO", pool="both", media="door_closing_chime.mp3")
J("D03-0005", "D03",
  "After a cricket match at a nearby stadium, the platform is filling faster than trains can clear it. What is the most effective first step?",
  "जवळच्या स्टेडियममधील क्रिकेट सामन्यानंतर प्लॅटफॉर्मवर, गाड्या नेऊ शकतात त्यापेक्षा वेगाने गर्दी भरत आहे. सर्वात परिणामकारक पहिला टप्पा कोणता?",
  [("Regulate entry at the gates and stairs, and ask OCC to add or hold trains", "गेट व जिन्यावर प्रवेश नियंत्रित करा व OCC ला जादा गाड्या किंवा थांबा मागा", 1.0),
   ("Make repeated PA announcements asking passengers to spread along the platform", "प्लॅटफॉर्मवर पसरून उभे राहण्यासाठी वारंवार PA उद्घोषणा करा", 0.5),
   ("Stop issuing tokens and QR tickets until the crowd reduces", "गर्दी कमी होईपर्यंत टोकन व QR तिकीट देणे थांबवा", 0.3),
   ("Set the AFC gates to free mode so people move through faster", "लोक लवकर पुढे जावेत म्हणून AFC गेट मुक्त मोडमध्ये ठेवा", 0.0)],
  lvl=3, bloom="Analyse", fam="SC,OCC,SFS",
  why="Control the inflow upstream, before the platform; announcements help but do not stop the build-up; free gates increase the flow onto the platform.",
  why_mr="प्लॅटफॉर्मपूर्वीच प्रवेश नियंत्रित करा; उद्घोषणांनी मदत होते पण गर्दी थांबत नाही; मुक्त गेटमुळे प्लॅटफॉर्मवर गर्दी अधिक वाढते.")

# ====================================================================== D04 TRAFFIC CONTROL
N("D04-0001", "D04",
  "A line's round trip, including turnaround time at both terminals, takes 66 minutes, and 11 trains are in service. What headway can be run (minutes)?",
  "दोन्ही टर्मिनलवरील वळणाच्या वेळेसह एका मार्गाची फेरी 66 मिनिटांची आहे आणि 11 गाड्या सेवेत आहेत. किती मिनिटांचा हेडवे देता येईल?",
  6, lvl=3, bloom="Apply", fam="OCC,TO,SC", why="Headway = round-trip time ÷ trains in service = 66 ÷ 11 = 6 minutes.", why_mr="हेडवे = फेरीचा वेळ ÷ सेवेतील गाड्या = 66 ÷ 11 = 6 मिनिटे.")
S("D04-0002", "D04", "A train is delayed 4 minutes at mid-line in the peak, and nothing is done. What usually happens next?",
  "गर्दीच्या वेळी एक गाडी मार्गाच्या मध्यात 4 मिनिटे उशिरा होते आणि काहीही केले जात नाही. पुढे सहसा काय होते?",
  [("Its gap grows, it gets later and the train behind catches up", "त्याच्यापुढील अंतर वाढते, तो आणखी उशिरा होतो व मागची गाडी जवळ येते", True),
   ("The delay stays the same until it is absorbed at the terminal", "टर्मिनलवर भरून निघेपर्यंत विलंब तेवढाच राहतो", False),
   ("All following trains are delayed by the same 4 minutes", "मागच्या सर्व गाड्यांना तेवढाच 4 मिनिटांचा विलंब होतो", False),
   ("ATS recovers the time automatically within two stations", "ATS दोन स्थानकांत वेळ आपोआप भरून काढते", False)],
  lvl=3, bloom="Understand", fam="OCC,TO", why="Delays grow into bunching: the late train meets more waiting passengers, dwells longer and falls further behind.",
  why_mr="विलंबातून गाड्यांची गर्दी (bunching) होते: उशिराच्या गाडीला अधिक प्रवासी मिळतात, ती जास्त थांबते व आणखी मागे पडते.")
S("D04-0003", "D04", "After a delay, the controller holds some following trains briefly at stations. Why?",
  "विलंबानंतर नियंत्रक मागच्या काही गाड्यांना स्थानकांवर थोडा वेळ थांबवतो. का?",
  [("To even out the gaps and restore a regular headway", "अंतर समान करून नियमित हेडवे पुन्हा आणण्यासाठी", True),
   ("To give the delayed train's crew a rest break", "उशिराच्या गाडीच्या कर्मचाऱ्यांना विश्रांती देण्यासाठी", False),
   ("To reduce the traction energy used in the peak", "गर्दीच्या वेळी ट्रॅक्शन ऊर्जा कमी करण्यासाठी", False),
   ("To let passengers on platforms buy tickets", "प्लॅटफॉर्मवरील प्रवाशांना तिकीट घेता यावे म्हणून", False)],
  lvl=2, bloom="Understand", fam="OCC", pool="practice")

# ====================================================================== D05 DEGRADED WORKING & EMERGENCIES
J("D05-0001", "D05",
  "Peak hour: a train is held between stations by a signalling failure and passengers on your platform are getting agitated. As Station Controller, what do you do first?",
  "गर्दीची वेळ: सिग्नलिंग बिघाडामुळे गाडी दोन स्थानकांदरम्यान थांबली आहे आणि प्लॅटफॉर्मवरील प्रवासी अस्वस्थ होत आहेत. स्टेशन कंट्रोलर म्हणून तुम्ही प्रथम काय कराल?",
  [("Coordinate with OCC and regulate entry at the AFC gates", "OCC शी समन्वय साधा व AFC गेटवर प्रवेश नियंत्रित करा", 1.0),
   ("Make regular PA announcements with the expected delay", "अपेक्षित विलंबासह नियमित PA उद्घोषणा करा", 0.6),
   ("Leave the SCR and calm passengers on the platform yourself", "SCR सोडून स्वतः प्लॅटफॉर्मवर प्रवाशांना शांत करा", 0.1),
   ("Wait for OCC instructions before making any announcement", "कोणतीही उद्घोषणा करण्यापूर्वी OCC च्या सूचनांची वाट पहा", 0.0)],
  lvl=3, bloom="Analyse", fam="SC,OCC", sme=True,
  why="Coordinated information plus crowd regulation keeps the station safe; the SCR must not be left unmanned; silence makes agitation worse.",
  why_mr="समन्वित माहिती व गर्दी नियमनामुळे स्थानक सुरक्षित राहते; SCR रिकामा ठेवू नये; मौनामुळे अस्वस्थता वाढते.")
Q("D05-0002", "D05", "Put the first actions for a confirmed fire at a station in the correct order.", "स्थानकावर आग निश्चित झाल्यावर पहिल्या कृती योग्य क्रमाने लावा.",
  [("Raise the alarm and inform OCC", "इशारा द्या व OCC ला कळवा"), ("Make the evacuation announcement", "बाहेर पडण्याची उद्घोषणा करा"),
   ("Set AFC gates to emergency free-exit mode", "AFC गेट आपत्कालीन मुक्त-निर्गम मोडमध्ये करा"),
   ("Guide passengers out and sweep all areas", "प्रवाशांना बाहेर न्या व सर्व भाग तपासा"), ("Report 'station clear' to OCC", "'स्थानक रिकामे' असे OCC ला कळवा")],
  lvl=3, fam="SC,OCC,SFS", sme=True, src="Station emergency SOP (to be cited)")
TF("D05-0003", "D05", "During a fire evacuation, lifts may be used for passengers with reduced mobility if the fire is on another floor.",
   "आग दुसऱ्या मजल्यावर असल्यास, आग-निर्वासनाच्या वेळी हालचाल-मर्यादा असलेल्या प्रवाशांसाठी लिफ्ट वापरता येतात.", False,
   lvl=2, bloom="Understand", fam=TECH, pool="practice", sme=True,
   why="Ordinary lifts must not be used in a fire; passengers with reduced mobility are helped by staff to refuge areas or down the stairs, as per the evacuation plan.",
   why_mr="आगीच्या वेळी सामान्य लिफ्ट वापरू नयेत; हालचाल-मर्यादा असलेल्या प्रवाशांना निर्वासन योजनेनुसार कर्मचारी आश्रय-क्षेत्रात किंवा जिन्याने खाली नेतात.")

PASSAGES.append(dict(code="P-0001", title_en="Incident at Station X – 08:42", title_mr="स्थानक X वरील घटना – 08:42",
    body_en=("At 08:42 the Station Controller noticed on CCTV that smoke was coming from a waste bin on platform 2. "
             "A train was due in 3 minutes. The Station Controller informed OCC, asked for the approaching train to be held at the previous station, "
             "and sent a staff member with a CO2 extinguisher. The fire was put out by 08:47. No one was hurt. Platform 2 was reopened at 09:05 after "
             "the area was checked and the bin was removed. Two trains were delayed by about 8 minutes."),
    body_mr=("08:42 वाजता स्टेशन कंट्रोलरला CCTV वर प्लॅटफॉर्म 2 वरील कचरापेटीतून धूर येताना दिसला. 3 मिनिटांत गाडी येणार होती. "
             "स्टेशन कंट्रोलरने OCC ला कळवले, येणारी गाडी मागच्या स्थानकावर थांबवण्याची विनंती केली आणि एका कर्मचाऱ्याला CO2 अग्निशामक घेऊन पाठवले. "
             "08:47 पर्यंत आग विझली. कोणालाही इजा झाली नाही. जागा तपासून कचरापेटी हटवल्यानंतर 09:05 वाजता प्लॅटफॉर्म 2 पुन्हा सुरू झाला. "
             "दोन गाड्यांना सुमारे 8 मिनिटे विलंब झाला.")))
S("D05-0004", "D05", "Read the passage. Why did the Station Controller ask OCC to hold the approaching train?",
  "उतारा वाचा. स्टेशन कंट्रोलरने येणारी गाडी थांबवण्याची विनंती OCC ला का केली?",
  [("So that passengers would not alight onto a platform with a fire", "आग असलेल्या प्लॅटफॉर्मवर प्रवासी उतरू नयेत म्हणून", True),
   ("So that the train crew could help to put out the fire", "गाडीचे कर्मचारी आग विझवण्यास मदत करू शकावेत म्हणून", False),
   ("Because OCC must approve every fire announcement first", "कारण प्रत्येक आग-उद्घोषणेला आधी OCC ची मंजुरी लागते", False),
   ("Because smoke can trip the traction supply of the section", "कारण धुरामुळे विभागाचा ट्रॅक्शन पुरवठा ट्रिप होऊ शकतो", False)],
  lvl=3, bloom="Analyse", fam="SC,OCC,TO,SFS", passage="P-0001", porder=1, pool="both")
N("D05-0005", "D05", "Read the passage. For how many minutes was platform 2 out of use (from 08:42 until it reopened)?",
  "उतारा वाचा. प्लॅटफॉर्म 2 किती मिनिटे वापराबाहेर होता (08:42 पासून पुन्हा सुरू होईपर्यंत)?",
  23, lvl=2, bloom="Apply", fam="SC,OCC,TO,SFS", passage="P-0001", porder=2, pool="both")
M("D05-0006", "D05", "Read the passage. Which details must the incident report include?", "उतारा वाचा. घटना अहवालात कोणते तपशील असले पाहिजेत?",
  [("Times of detection, extinguishing and reopening", "शोध, विझवणे व पुन्हा सुरू होण्याच्या वेळा", True),
   ("Action taken and the extinguisher type used", "केलेली कृती व वापरलेल्या अग्निशामकाचा प्रकार", True),
   ("Effect on train service, with delays", "गाडी सेवेवरील परिणाम व विलंब", True),
   ("Names and phone numbers of passengers on the platform", "प्लॅटफॉर्मवरील प्रवाशांची नावे व फोन क्रमांक", False),
   ("The cost of replacing the waste bin", "कचरापेटी बदलण्याचा खर्च", False)],
  lvl=3, bloom="Analyse", fam="SC,OCC,TO,SFS", passage="P-0001", porder=3, pool="both")

# ====================================================================== D06 AFC & REVENUE
S("D06-0001", "D06", "A passenger's QR ticket is rejected at the entry gate. What should station staff do?", "प्रवाशाचे QR तिकीट प्रवेश गेटवर नाकारले जाते. स्थानक कर्मचाऱ्यांनी काय करावे?",
  [("Analyse the ticket at the TOM and resolve it as per AFC rules", "TOM वर तिकीट तपासा व AFC नियमांनुसार प्रश्न सोडवा", True),
   ("Let the passenger in by the staff gate and note the ticket number", "कर्मचारी गेटने प्रवाशाला आत सोडा व तिकीट क्रमांक नोंदवा", False),
   ("Issue a fresh QR ticket free of charge to avoid an argument", "वाद टाळण्यासाठी नवीन QR तिकीट मोफत द्या", False),
   ("Ask the passenger to buy a new ticket and claim a refund online", "नवीन तिकीट घेऊन ऑनलाइन परतावा मागण्यास प्रवाशाला सांगा", False)],
  lvl=2, fam="SC,STA,COM")
N("D06-0002", "D06",
  "During a shift a ticket office takes ₹18,450 in cash ticket sales and ₹6,200 in cash card top-ups, and pays ₹650 in refunds. The ₹5,000 float stays in the drawer. How much revenue cash (₹) must be deposited?",
  "एका पाळीत तिकीट कार्यालयाला रोख तिकीट विक्रीतून ₹18,450 व रोख कार्ड टॉप-अपमधून ₹6,200 मिळतात आणि ₹650 परतावा दिला जातो. ₹5,000 ची सुरुवातीची रक्कम ड्रॉवरमध्येच राहते. किती महसूल रोख (₹) जमा करावी?",
  24000, lvl=3, bloom="Apply", fam="SC,FA,COM", why="18,450 + 6,200 − 650 = 24,000; the float is not revenue.", why_mr="18,450 + 6,200 − 650 = 24,000; सुरुवातीची रक्कम महसूल नाही.")
TF("D06-0003", "D06", "If the cash count at shift end is ₹50 short, the operator may make up the shortage personally and close the shift without reporting it.",
   "पाळीच्या शेवटी रोख मोजणीत ₹50 कमी आढळल्यास, ऑपरेटर स्वतः ती रक्कम भरून न कळवता पाळी बंद करू शकतो.", False,
   lvl=2, bloom="Understand", fam="SC,FA,COM", pool="practice",
   why="Every shortage or excess must be recorded and reported; silent make-good hides errors and fraud.",
   why_mr="प्रत्येक कमतरता किंवा जादा रक्कम नोंदवून कळवलीच पाहिजे; गुपचूप भरपाईमुळे चुका व गैरव्यवहार लपतात.")

# ====================================================================== D07 ROLLING STOCK
S("D07-0001", "D07", "What is the main job of the TCMS on a metro train?", "मेट्रो गाडीवरील TCMS चे मुख्य काम काय आहे?",
  [("Monitor train subsystems, show faults on the DMI and log events", "गाडीच्या उप-प्रणालींवर लक्ष ठेवणे, DMI वर दोष दाखवणे व घटना नोंदवणे", True),
   ("Calculate the movement authority sent by the zone controller", "झोन कंट्रोलरकडून येणारा हालचाल-अधिकार ठरवणे", False),
   ("Supply auxiliary power to the lighting and air-conditioning", "प्रकाश व वातानुकूलनाला सहाय्यक वीज पुरवणे", False),
   ("Supervise the traction power supply of the line section", "मार्ग-विभागाच्या ट्रॅक्शन वीजपुरवठ्यावर देखरेख ठेवणे", False)],
  lvl=2, bloom="Understand", fam="RS,TO,OCC",
  why="The other options describe CBTC zone control, the auxiliary power supply and SCADA – systems often confused with TCMS.",
  why_mr="इतर पर्याय CBTC झोन नियंत्रण, सहाय्यक वीजपुरवठा व SCADA यांचे वर्णन करतात – ज्या प्रणाली अनेकदा TCMS शी गोंधळल्या जातात.")
M("D07-0002", "D07", "Which braking systems are found on a typical metro train?", "सामान्य मेट्रो गाडीवर कोणत्या ब्रेक प्रणाली असतात?",
  [("Electric (regenerative) brake", "विद्युत (रीजनरेटिव्ह) ब्रेक", True), ("Pneumatic friction brake", "न्यूमॅटिक घर्षण ब्रेक", True),
   ("Spring-applied parking brake", "स्प्रिंगने लागणारा पार्किंग ब्रेक", True), ("Wayside brake applied from SCADA", "SCADA वरून लावला जाणारा मार्गालगतचा ब्रेक", False),
   ("Brake applied by the platform screen doors", "प्लॅटफॉर्म स्क्रीन दरवाजांनी लावला जाणारा ब्रेक", False)],
  lvl=2, bloom="Remember", fam="RS,TO")
S("D07-0003", "D07", "On Maha Metro trains, which component collects traction current from the line?", "महा मेट्रोच्या गाड्यांवर मार्गावरून ट्रॅक्शन विद्युत प्रवाह कोणता भाग घेतो?",
  [("Pantograph", "पॅन्टोग्राफ", True), ("Collector shoe", "कलेक्टर शू", False), ("Earth return brush", "अर्थ रिटर्न ब्रश", False), ("Line breaker", "लाइन ब्रेकर", False)],
  lvl=1, bloom="Remember", fam="RS,TO,TPS", pool="practice", why="Overhead (OHE) supply is collected by the pantograph; collector shoes are used on third-rail systems.",
  why_mr="ओव्हरहेड (OHE) पुरवठा पॅन्टोग्राफद्वारे घेतला जातो; कलेक्टर शू तिसऱ्या-रूळ प्रणालीत वापरतात.")
S("D07-0004", "D07",
  "During train preparation, main reservoir pressure builds up slowly and the compressor keeps running. What is the most likely cause to check first?",
  "गाडीच्या तयारीदरम्यान मुख्य जलाशयातील दाब हळूहळू वाढतो आणि कॉम्प्रेसर सतत चालू राहतो. आधी तपासायचे सर्वात संभाव्य कारण कोणते?",
  [("An air leak in the brake or main reservoir pipework", "ब्रेक किंवा मुख्य जलाशयाच्या पाइपमध्ये हवा गळती", True),
   ("Low battery voltage during the train start-up", "गाडी सुरू होताना बॅटरीचा कमी व्होल्टेज", False),
   ("An overloaded air-conditioning unit on one car", "एका डब्यातील ओव्हरलोड झालेले वातानुकूलन यंत्र", False),
   ("A worn carbon strip on the pantograph", "पॅन्टोग्राफवरील झिजलेली कार्बन पट्टी", False)],
  lvl=3, bloom="Analyse", fam="RS", sme=True)

# ====================================================================== D08 SIGNALLING & TRAIN CONTROL
S("D08-0001", "D08", "What is the main purpose of interlocking?", "इंटरलॉकिंगचा मुख्य उद्देश काय आहे?",
  [("Preventing conflicting routes, points and signals", "परस्परविरोधी मार्ग, पॉइंट्स व सिग्नल रोखणे", True),
   ("Calculating the safe braking curve for each train", "प्रत्येक गाडीसाठी सुरक्षित ब्रेकिंग वक्र ठरवणे", False),
   ("Detecting broken rails within the track sections", "रूळ-विभागांतील तुटलेले रूळ शोधणे", False),
   ("Scheduling trains to the timetable automatically", "वेळापत्रकानुसार गाड्यांचे आपोआप नियोजन करणे", False)],
  lvl=2, bloom="Understand", fam="STA,OCC,TO",
  why="Braking curves belong to ATP, broken-rail detection to track circuits and scheduling to ATS.", why_mr="ब्रेकिंग वक्र ATP चे, तुटलेला रूळ शोधणे ट्रॅक सर्किटचे व नियोजन ATS चे काम आहे.")
S("D08-0002", "D08",
  "After a power interruption, an axle-counter section shows 'occupied' although no train is in it. What is generally required before normal working resumes?",
  "वीज खंडित झाल्यानंतर, गाडी नसतानाही एक ॲक्सल-काउंटर विभाग 'व्यापलेला' दाखवतो. सामान्य कामकाज सुरू होण्यापूर्वी सामान्यतः काय आवश्यक असते?",
  [("Confirm the section is clear as laid down, then reset by the procedure", "नियमानुसार विभाग मोकळा असल्याची खात्री करा, मग कार्यपद्धतीनुसार रीसेट करा", True),
   ("Reset it at once from the SCR, as no train is shown in the section", "विभागात गाडी दिसत नसल्याने SCR वरून लगेच रीसेट करा", False),
   ("Wait for the next train to pass, which will clear it on its own", "पुढची गाडी जाऊ द्या, त्याने ते आपोआप मोकळे होईल", False),
   ("Replace the counting head, as the fault means it is damaged", "काउंटिंग हेड बदला, कारण दोष म्हणजे ते खराब झाले आहे", False)],
  lvl=3, bloom="Apply", fam="STA,OCC", sme=True)
S("D08-0003", "D08", "Your train stands at the signal shown. OCC tells you by radio to pass it. What must you do before moving?",
  "दाखवलेल्या सिग्नलवर तुमची गाडी उभी आहे. OCC रेडिओवर तो ओलांडण्यास सांगते. हलवण्यापूर्वी तुम्ही काय करणे आवश्यक आहे?",
  [("Repeat the authority back, then go at restricted speed", "अधिकार पुन्हा उच्चारा, मग प्रतिबंधित वेगाने जा", True),
   ("Move at normal speed, as OCC has already given permission", "OCC ने परवानगी दिल्याने सामान्य वेगाने जा", False),
   ("Ask the Station Controller to confirm the route is set", "मार्ग सेट असल्याची स्टेशन कंट्रोलरकडून खात्री घ्या", False),
   ("Wait for the signal to clear, whatever OCC instructs", "OCC काहीही सांगो, सिग्नल मोकळा होण्याची वाट पहा", False)],
  lvl=2, bloom="Apply", fam="TO,STA,OCC,SC", media="signal_red.png", sme=True)
TF("D08-0004", "D08", "CBTC relies on continuous two-way radio communication between the train and wayside equipment.", "CBTC गाडी व मार्गालगतच्या उपकरणांमधील सतत द्विमार्गी रेडिओ संवादावर अवलंबून असते.",
   True, lvl=1, bloom="Remember", fam="STA,OCC,TO", pool="practice")
S("D08-0005", "D08", "Watch the clip. Which aspect does the signal show at the end?", "क्लिप पहा. शेवटी सिग्नल कोणता संकेत दाखवतो?",
  [("Red", "लाल", True), ("Yellow", "पिवळा", False), ("Green", "हिरवा", False), ("Flashing yellow", "लुकलुकणारा पिवळा", False)],
  lvl=1, bloom="Remember", fam="TO,STA,OCC,SC", media="signal_sequence.mp4", pool="practice")

# ====================================================================== D09 TELECOM & SCADA
S("D09-0001", "D09", "The OCC power controller opens a traction breaker remotely. Which system carries this command?", "OCC पॉवर कंट्रोलर दूरस्थपणे ट्रॅक्शन ब्रेकर उघडतो. ही आज्ञा कोणती प्रणाली पोहोचवते?",
  [("SCADA", "SCADA", True), ("Train radio", "ट्रेन रेडिओ", False), ("PIDS", "PIDS", False), ("Master clock", "मास्टर क्लॉक", False)],
  lvl=2, bloom="Understand", fam="STA,OCC,TPS,EM,IT")
X("D09-0002", "D09", "Match each telecom system with its use.", "प्रत्येक दूरसंचार प्रणालीची तिच्या उपयोगाशी जोडणी करा.",
  [(("PIDS", "PIDS"), ("Train information for passengers", "प्रवाशांसाठी गाडीची माहिती")), (("Radio", "रेडिओ"), ("Voice communication between staff", "कर्मचाऱ्यांमधील आवाज संवाद")),
   (("CCTV", "CCTV"), ("Video surveillance", "व्हिडिओ देखरेख")), (("Master clock", "मास्टर क्लॉक"), ("Time synchronisation of systems", "प्रणालींचे वेळ समक्रमण"))],
  lvl=2, bloom="Remember", fam="STA,SC,OCC", pool="practice")
S("D09-0003", "D09", "Police ask for CCTV footage of a theft at your station. What should be done?", "तुमच्या स्थानकावरील चोरीचे CCTV फुटेज पोलीस मागतात. काय करावे?",
  [("Take a written request, export the clip and log the handover", "लेखी विनंती घ्या, क्लिप काढा व हस्तांतराची नोंद करा", True),
   ("Hand over the recorder's hard disk so they get all of the footage", "सर्व फुटेज मिळावे म्हणून रेकॉर्डरची हार्ड डिस्क द्या", False),
   ("Play the footage for them in the SCR, but do not give any copy", "SCR मध्ये फुटेज दाखवा, पण प्रत देऊ नका", False),
   ("Ask them to come back with a court order before doing anything", "काहीही करण्यापूर्वी न्यायालयीन आदेश आणण्यास सांगा", False)],
  lvl=3, bloom="Apply", fam="STA,SC,SFS,IT", sme=True)

# ====================================================================== D10 TRACTION POWER
S("D10-0001", "D10", "What is the main role of a Receiving Sub-Station (RSS)?", "रिसीव्हिंग सब-स्टेशन (RSS) ची मुख्य भूमिका काय आहे?",
  [("Take grid supply and step it down for traction and auxiliaries", "ग्रिड पुरवठा घेऊन ट्रॅक्शन व सहाय्यक भारासाठी कमी करणे", True),
   ("Convert AC to DC for a third-rail traction system", "तिसऱ्या-रूळ ट्रॅक्शनसाठी AC चे DC मध्ये रूपांतर करणे", False),
   ("Store regenerated braking energy in station batteries", "रीजनरेटिव्ह ब्रेकिंग ऊर्जा स्थानकांच्या बॅटरीत साठवणे", False),
   ("Supply power to the signalling and telecom systems only", "फक्त सिग्नलिंग व दूरसंचार प्रणालींना वीज पुरवणे", False)],
  lvl=2, bloom="Understand", fam="TPS,OCC")
TF("D10-0002", "D10", "The OHE must be treated as live until it has been isolated, tested dead and earthed under a valid permit.",
   "वैध परवान्याअंतर्गत OHE वेगळी करून, मृत असल्याची चाचणी करून व अर्थिंग करेपर्यंत ती चालूच मानली पाहिजे.", True, lvl=2, bloom="Understand", fam="TPS,RS,CT,OCC,EM")
S("D10-0003", "D10",
  "Your team has a power block for OHE work. The TPC confirms the section is switched off. What must happen before anyone touches the OHE?",
  "OHE कामासाठी तुमच्या टीमला पॉवर ब्लॉक मिळाला आहे. TPC विभाग बंद असल्याची खात्री देतो. कोणीही OHE ला स्पर्श करण्यापूर्वी काय झाले पाहिजे?",
  [("Test for absence of voltage and apply earths on both sides", "व्होल्टेज नसल्याची चाचणी करा व दोन्ही बाजूंना अर्थ लावा", True),
   ("Wait five minutes so any residual charge drains away", "उरलेला भार निघून जावा म्हणून पाच मिनिटे थांबा", False),
   ("Apply one earth near the work site, then start the work", "कामाच्या ठिकाणाजवळ एक अर्थ लावा, मग काम सुरू करा", False),
   ("Check that the SCADA screen shows the breaker as open", "SCADA स्क्रीनवर ब्रेकर उघडा दिसतो याची खात्री करा", False)],
  lvl=3, bloom="Apply", fam="TPS,CT,RS", pool="practice", sme=True)

# ====================================================================== D11 E&M BUILDING SYSTEMS
S("D11-0001", "D11", "A child's shoe gets caught at the comb plate of a moving escalator. What should you do first?", "चालू एस्केलेटरच्या कोम्ब प्लेटमध्ये मुलाचा बूट अडकतो. तुम्ही प्रथम काय कराल?",
  [("Press the escalator emergency stop at once", "एस्केलेटरचे आपत्कालीन थांबा बटण लगेच दाबा", True),
   ("Help the parent pull the child's foot free", "पालकांना मुलाचा पाय ओढून सोडवण्यास मदत करा", False),
   ("Reverse the escalator so the shoe is released", "बूट सुटावा म्हणून एस्केलेटरची दिशा उलटा करा", False),
   ("Switch it off at the panel in the E&M room", "E&M कक्षातील पॅनेलवरून ते बंद करा", False)],
  lvl=2, bloom="Apply", fam="EM,SC,SFS")
Q("D11-0002", "D11", "Put the steps to rescue passengers from a stalled lift in the correct order.", "बंद पडलेल्या लिफ्टमधून प्रवाशांची सुटका करण्याचे टप्पे क्रमाने लावा.",
  [("Reassure passengers over the intercom", "इंटरकॉमवर प्रवाशांना धीर द्या"), ("Isolate the lift's main power supply", "लिफ्टचा मुख्य वीजपुरवठा वेगळा करा"),
   ("Confirm the position of the car", "कारची स्थिती निश्चित करा"), ("Move the car to the nearest landing", "कार जवळच्या मजल्यावर आणा"),
   ("Open the doors with the landing key", "लँडिंग कीने दरवाजे उघडा")],
  lvl=3, fam="EM,SC", sme=True, src="Lift OEM rescue procedure; Lift & Escalator Simulator at CoE")
X("D11-0003", "D11", "Match each E&M system with its function.", "प्रत्येक E&M प्रणालीची कार्याशी जोडणी करा.",
  [(("TVS", "TVS"), ("Tunnel ventilation and smoke control", "बोगदा वायुवीजन व धूर नियंत्रण")), (("BMS", "BMS"), ("Monitoring building services", "इमारत सेवांचे निरीक्षण")),
   (("FDAS", "FDAS"), ("Fire detection and alarm", "आग शोध व इशारा")), (("UPS", "UPS"), ("Backup power for critical loads", "महत्त्वाच्या भारासाठी राखीव वीज"))],
  lvl=2, bloom="Remember", fam="EM,SC,OCC", pool="practice")

# ====================================================================== D12 CIVIL, TRACK & STRUCTURES
S("D12-0001", "D12", "During a track patrol you find a crack at a rail weld on the running line. What should you do first?", "रूळ-गस्तीदरम्यान धावत्या मार्गावर रूळाच्या वेल्डला भेग आढळते. तुम्ही प्रथम काय कराल?",
  [("Inform OCC at once so trains are stopped or restricted", "गाड्या थांबवाव्या किंवा मर्यादित कराव्या म्हणून लगेच OCC ला कळवा", True),
   ("Mark it with paint and include it in the end-of-shift report", "रंगाने खूण करा व पाळीअखेरच्या अहवालात नोंदवा", False),
   ("Fit joggled fishplates yourself before the first train", "पहिल्या गाडीपूर्वी स्वतः जॉगल्ड फिशप्लेट लावा", False),
   ("Finish the patrol first and then report all defects together", "आधी गस्त पूर्ण करा व मग सर्व दोष एकत्र कळवा", False)],
  lvl=3, bloom="Apply", fam="CT,PJ,OCC", sme=True)
TF("D12-0002", "D12", "Long welded rails need monitoring of rail temperature because of thermal stresses.", "उष्णतेमुळे होणाऱ्या ताणामुळे लाँग वेल्डेड रेलच्या तापमानावर लक्ष ठेवावे लागते.", True,
   lvl=2, bloom="Understand", fam="CT,PJ")
S("D12-0003", "D12", "Ultrasonic flaw detection (USFD) of rails mainly finds:", "रुळांच्या अल्ट्रासोनिक दोष-शोध (USFD) चाचणीत मुख्यतः काय आढळते?",
  [("Internal flaws and cracks", "आतील दोष व भेगा", True), ("Gauge widening", "गेज रुंदावणे", False),
   ("Railhead wear profile", "रेलहेड झीज प्रोफाइल", False), ("Ballast fouling", "खडी दूषित होणे", False)],
  lvl=2, bloom="Remember", fam="CT", pool="practice")
S("D12-0004", "D12", "What is the track gauge of Maha Metro's standard-gauge lines?", "महा मेट्रोच्या स्टँडर्ड-गेज मार्गांचे गेज किती आहे?",
  [("1435 mm", "1435 मिमी", True), ("1676 mm", "1676 मिमी", False), ("1000 mm", "1000 मिमी", False), ("1520 mm", "1520 मिमी", False)],
  lvl=1, bloom="Remember", fam="CT,PJ,RS", pool="practice")

# ====================================================================== D13 ELECTRICAL SAFETY & PERMIT-TO-WORK
Q("D13-0001", "D13", "Arrange the steps before starting work on isolated electrical equipment.", "वेगळ्या केलेल्या विद्युत उपकरणावर काम सुरू करण्यापूर्वीचे टप्पे क्रमाने लावा.",
  [("Obtain the permit-to-work", "कार्य-परवाना मिळवा"), ("Isolate and lock out the supply", "वीजपुरवठा वेगळा करून कुलूपबंद करा"),
   ("Test for absence of voltage", "व्होल्टेज नसल्याची चाचणी करा"), ("Apply earthing", "अर्थिंग लावा")],
  lvl=3, fam="RS,STA,TPS,EM,CT,OCC,SFS,PJ", sme=True, src="Permit-to-work SOP (to be cited)",
  why="Work authority first, then isolate and lock, prove dead, then earth. Earthing before proving dead is unsafe.",
  why_mr="आधी कार्य-अधिकार, मग वेगळे करून कुलूप, मृत असल्याची खात्री, मग अर्थिंग. खात्रीपूर्वी अर्थिंग असुरक्षित आहे.")
S("D13-0002", "D13", "Why does each worker put a personal lock on the isolation point (lock-out/tag-out)?", "प्रत्येक कामगार विलगीकरण बिंदूवर वैयक्तिक कुलूप (लॉक-आउट/टॅग-आउट) का लावतो?",
  [("So the supply cannot be restored while anyone is still working", "कोणी काम करत असताना पुरवठा पुन्हा सुरू होऊ नये म्हणून", True),
   ("So the supervisor can see who attended the job that day", "त्या दिवशी कामावर कोण होते ते पर्यवेक्षकाला कळावे म्हणून", False),
   ("So the equipment cannot be stolen during the shutdown", "बंद काळात उपकरण चोरीला जाऊ नये म्हणून", False),
   ("So the permit can be closed without the issuer present", "जारीकर्ता नसतानाही परवाना बंद करता यावा म्हणून", False)],
  lvl=2, bloom="Understand", fam="RS,STA,TPS,EM,CT,SFS")
TF("D13-0003", "D13", "A permit-to-work may be cancelled only after all staff and materials are clear and the earths are removed.",
   "सर्व कर्मचारी व साहित्य बाजूला झाल्यावर व अर्थ काढल्यावरच कार्य-परवाना रद्द करता येतो.", True, lvl=2, bloom="Understand", fam="RS,STA,TPS,EM,CT", pool="practice", sme=True)
J("D13-0004", "D13",
  "Your supervisor asks you to quickly replace a fuse in a live LT panel to avoid delaying service. No permit has been issued. What do you do?",
  "सेवा उशिरा होऊ नये म्हणून पर्यवेक्षक तुम्हाला चालू LT पॅनेलमधील फ्यूज पटकन बदलण्यास सांगतात. परवाना दिलेला नाही. तुम्ही काय कराल?",
  [("Explain that isolation and a permit are needed, and arrange them", "विलगीकरण व परवाना आवश्यक असल्याचे सांगा व ते मिळवा", 1.0),
   ("Do it wearing insulated gloves and using insulated tools", "इन्सुलेटेड हातमोजे व साधने वापरून ते करा", 0.1),
   ("Do it, but ask a colleague to stand by the panel", "करा, पण एका सहकाऱ्याला पॅनेलजवळ उभे राहण्यास सांगा", 0.1),
   ("Refuse and leave the site until a permit is issued", "नकार द्या व परवाना मिळेपर्यंत जागा सोडा", 0.4)],
  lvl=3, fam="EM,TPS,STA,RS")

# ====================================================================== D14 MAINTENANCE MANAGEMENT & RAMS
N("D14-0001", "D14", "A fleet ran 12,50,000 car-km last quarter with 50 service-affecting failures. What is the MDBF (km)?",
  "मागील तिमाहीत ताफ्याने 12,50,000 कार-किमी धाव घेतली आणि सेवेवर परिणाम करणारे 50 बिघाड झाले. MDBF (किमी) किती?", 25000,
  lvl=2, bloom="Apply", fam="RS,STA,TPS,EM,CT", why="MDBF = distance ÷ failures = 12,50,000 ÷ 50 = 25,000 km.", why_mr="MDBF = अंतर ÷ बिघाड = 12,50,000 ÷ 50 = 25,000 किमी.")
N("D14-0002", "D14", "An equipment type had 5 failures last month with a total repair time of 10 hours. What is the MTTR in hours?",
  "एका उपकरण प्रकारात मागील महिन्यात 5 बिघाड झाले व एकूण दुरुस्ती वेळ 10 तास होता. MTTR किती तास?", 2, lvl=2, bloom="Apply", fam="RS,STA,TPS,EM,CT", pool="practice",
  why="MTTR = total repair time ÷ number of repairs = 10 ÷ 5 = 2 hours.", why_mr="MTTR = एकूण दुरुस्ती वेळ ÷ दुरुस्त्यांची संख्या = 10 ÷ 5 = 2 तास.")
S("D14-0003", "D14", "Which data best shows that a preventive maintenance interval is too long?", "प्रतिबंधात्मक देखभालीचा कालावधी खूप लांब आहे हे कोणती माहिती सर्वात चांगली दाखवते?",
  [("Failures clustering just before each PM due date", "प्रत्येक PM देय तारखेच्या आधी बिघाड एकवटणे", True),
   ("Total maintenance hours booked in the last quarter", "मागील तिमाहीत नोंदवलेले एकूण देखभाल तास", False),
   ("Number of spare parts issued from stores", "भांडारातून दिलेल्या सुट्या भागांची संख्या", False),
   ("Overtime hours worked by the maintenance team", "देखभाल टीमने केलेले जादा तास", False)],
  lvl=4, bloom="Analyse", fam="RS,STA,TPS,EM,CT")

# ====================================================================== D15 DESIGN & ENGINEERING REVIEW
S("D15-0001", "D15", "What is an interface management plan used for in a metro project?", "मेट्रो प्रकल्पात इंटरफेस व्यवस्थापन योजना कशासाठी वापरतात?",
  [("Defining who delivers what where two systems or contracts meet", "दोन प्रणाली किंवा करार जिथे भेटतात तिथे कोण काय देणार ते ठरवणे", True),
   ("Recording design changes after the contract is awarded", "करार दिल्यानंतरचे आराखडा बदल नोंदवणे", False),
   ("Planning the order in which stations are opened", "स्थानके कोणत्या क्रमाने सुरू होतील याचे नियोजन", False),
   ("Listing the user-interface screens of each system", "प्रत्येक प्रणालीच्या वापरकर्ता-स्क्रीनची यादी करणे", False)],
  lvl=3, bloom="Understand", fam="PJ,RS,STA,TPS,EM,CT")
S("D15-0002", "D15", "Which Indian Standard is the code of practice for plain and reinforced concrete?", "साध्या व प्रबलित काँक्रीटसाठी कोणते भारतीय मानक आहे?",
  [("IS 456", "IS 456", True), ("IS 800", "IS 800", False), ("IS 1893", "IS 1893", False), ("IS 875", "IS 875", False)], lvl=2, bloom="Remember", fam="PJ,CT", pool="practice")
S("D15-0003", "D15",
  "The civil contractor proposes to reduce an escalator well opening by 150 mm to suit a beam. Before approval, who must review the change?",
  "बीमला जुळवून घेण्यासाठी स्थापत्य कंत्राटदार एस्केलेटर विहिरीचे उघडे 150 मिमीने कमी करण्याचा प्रस्ताव देतो. मंजुरीपूर्वी हा बदल कोणी तपासला पाहिजे?",
  [("The E&M (escalator) designer through the interface process", "इंटरफेस प्रक्रियेद्वारे E&M (एस्केलेटर) आराखडाकार", True),
   ("Only the structural designer, as it is a civil change", "फक्त संरचना आराखडाकार, कारण हा स्थापत्य बदल आहे", False),
   ("Only the architect, as it affects the station layout", "फक्त वास्तुविशारद, कारण स्थानकाच्या मांडणीवर परिणाम होतो", False),
   ("No one, as 150 mm is within construction tolerance", "कोणीही नाही, कारण 150 मिमी बांधकाम सहनशीलतेत आहे", False)],
  lvl=3, bloom="Analyse", fam="PJ,EM,CT")

# ====================================================================== D16 PROJECT PLANNING
N("D16-0001", "D16", "Earned value is ₹80 lakh and planned value is ₹100 lakh. What is the Schedule Performance Index (SPI)?",
  "अर्जित मूल्य ₹80 लाख व नियोजित मूल्य ₹100 लाख आहे. शेड्यूल परफॉर्मन्स इंडेक्स (SPI) किती?", 0.8, tol=0.001, lvl=3, bloom="Apply", fam="PJ,PCS",
  why="SPI = EV ÷ PV = 80 ÷ 100 = 0.8 (behind schedule).", why_mr="SPI = EV ÷ PV = 80 ÷ 100 = 0.8 (वेळापत्रकापेक्षा मागे).")
S("D16-0002", "D16", "What does the critical path of a project schedule show?", "प्रकल्प वेळापत्रकाचा क्रिटिकल पाथ काय दाखवतो?",
  [("The chain of activities that sets the shortest duration", "किमान कालावधी ठरवणारी कामांची साखळी", True),
   ("The activities that carry the highest cost in the budget", "अर्थसंकल्पातील सर्वाधिक खर्चाची कामे", False),
   ("The activities with the greatest safety risk", "सर्वाधिक सुरक्षा-जोखमीची कामे", False),
   ("The activities with the most float available", "सर्वाधिक फ्लोट उपलब्ध असलेली कामे", False)],
  lvl=2, bloom="Understand", fam="PJ", pool="practice")
S("D16-0003", "D16", "An activity with 10 days of total float is delayed by 6 days. Nothing else changes. What happens to the project finish date?",
  "10 दिवसांचा एकूण फ्लोट असलेले एक काम 6 दिवस उशिरा होते. बाकी काहीही बदलत नाही. प्रकल्प पूर्ण होण्याच्या तारखेचे काय होते?",
  [("It does not change", "ती बदलत नाही", True), ("It moves by 6 days", "ती 6 दिवसांनी पुढे जाते", False),
   ("It moves by 4 days", "ती 4 दिवसांनी पुढे जाते", False), ("It moves by 16 days", "ती 16 दिवसांनी पुढे जाते", False)],
  lvl=3, bloom="Apply", fam="PJ", why="The delay (6 days) is within the float (10 days), so the finish date holds; 4 days of float remain.",
  why_mr="विलंब (6 दिवस) फ्लोटमध्ये (10 दिवस) बसतो, त्यामुळे पूर्णत्वाची तारीख कायम राहते; 4 दिवसांचा फ्लोट उरतो.")

# ====================================================================== D17 QA/QC & CONSTRUCTION SAFETY
S("D17-0001", "D17", "28-day cube results for a pier pour are below the specified strength. What is the right first step?",
  "एका पियर ओतकामाचे 28 दिवसांचे क्यूब निकाल निर्धारित ताकदीपेक्षा कमी आहेत. योग्य पहिला टप्पा कोणता?",
  [("Raise an NCR and hold related work while it is investigated", "NCR काढा व तपास होईपर्यंत संबंधित काम थांबवा", True),
   ("Accept it, as concrete keeps gaining strength after 28 days", "स्वीकारा, कारण 28 दिवसांनंतरही काँक्रीटची ताकद वाढते", False),
   ("Cast fresh cubes from the next pour and use those results", "पुढील ओतकामातून नवे क्यूब घ्या व ते निकाल वापरा", False),
   ("Break out the pier and recast it straight away", "पियर लगेच तोडून पुन्हा ओता", False)],
  lvl=3, bloom="Apply", fam="PJ,CT,SFS")
TF("D17-0002", "D17", "Work must not proceed beyond a 'hold point' in the inspection and test plan until it is formally released.",
   "तपासणी व चाचणी योजनेतील 'होल्ड पॉइंट' औपचारिकरीत्या मोकळा होईपर्यंत काम पुढे नेऊ नये.", True, lvl=2, bloom="Understand", fam="PJ,CT,SFS")
S("D17-0003", "D17", "Concrete cube strength for acceptance is normally checked at:", "स्वीकृतीसाठी काँक्रीट क्यूबची ताकद सामान्यतः केव्हा तपासतात?",
  [("28 days", "28 दिवस", True), ("7 days", "7 दिवस", False), ("3 days", "3 दिवस", False), ("56 days", "56 दिवस", False)],
  lvl=1, bloom="Remember", fam="PJ,CT", pool="practice")

# ====================================================================== D18 SAFETY MANAGEMENT SYSTEM
Q("D18-0001", "D18", "Arrange the hierarchy of controls from most to least effective.", "नियंत्रणांची श्रेणी सर्वाधिक ते सर्वात कमी परिणामकारक अशी लावा.",
  [("Elimination", "निर्मूलन"), ("Substitution", "पर्याय"), ("Engineering controls", "अभियांत्रिकी नियंत्रणे"),
   ("Administrative controls", "प्रशासकीय नियंत्रणे"), ("Personal protective equipment", "वैयक्तिक संरक्षक साधने")], lvl=3, bloom="Understand", fam=TECH)
S("D18-0002", "D18", "Which control most reduces the risk of a passenger falling from the platform onto the track?",
  "प्रवासी प्लॅटफॉर्मवरून रुळावर पडण्याची जोखीम कोणते नियंत्रण सर्वाधिक कमी करते?",
  [("Platform screen doors", "प्लॅटफॉर्म स्क्रीन दरवाजे", True), ("A yellow safety line", "पिवळी सुरक्षा रेषा", False),
   ("Frequent PA reminders", "वारंवार PA सूचना", False), ("Extra platform staff", "जादा प्लॅटफॉर्म कर्मचारी", False)],
  lvl=3, bloom="Apply", fam=TECH, why="A physical barrier (engineering control) works without relying on people's behaviour; markings, announcements and staff are administrative controls.",
  why_mr="भौतिक अडथळा (अभियांत्रिकी नियंत्रण) लोकांच्या वर्तनावर अवलंबून न राहता काम करतो; रेषा, उद्घोषणा व कर्मचारी ही प्रशासकीय नियंत्रणे आहेत.")
S("D18-0003", "D18", "What is a hazard log used for?", "हॅझर्ड लॉग कशासाठी वापरतात?",
  [("Tracking each hazard and its controls until closure", "प्रत्येक धोका व त्याची नियंत्रणे पूर्ण होईपर्यंत मागोवा घेणे", True),
   ("Recording accidents after they have happened", "अपघात घडल्यानंतर त्यांची नोंद करणे", False),
   ("Listing safety equipment issued to each staff member", "प्रत्येक कर्मचाऱ्याला दिलेल्या सुरक्षा साधनांची यादी", False),
   ("Scheduling safety audits for the coming year", "पुढील वर्षाच्या सुरक्षा लेखापरीक्षांचे नियोजन", False)],
  lvl=2, bloom="Remember", fam=TECH, pool="practice")

# ====================================================================== D19 ACCOUNTING
S("D19-0001", "D19", "March electricity charges of ₹4.2 lakh are billed by the supplier in April. In which financial year are they recorded?",
  "मार्चचे ₹4.2 लाख वीज शुल्क पुरवठादार एप्रिलमध्ये बिल करतो. ते कोणत्या आर्थिक वर्षात नोंदवले जाते?",
  [("The year ending in March, as an accrued expense", "मार्चमध्ये संपणाऱ्या वर्षात, उपार्जित खर्च म्हणून", True),
   ("The new year, when the bill is received in April", "नव्या वर्षात, एप्रिलमध्ये बिल मिळाल्यावर", False),
   ("Whichever year the payment is actually made in", "प्रत्यक्ष देयक ज्या वर्षात होईल त्या वर्षात", False),
   ("Half in each year, as it spans the year-end", "वर्षअखेर ओलांडते म्हणून दोन्ही वर्षांत अर्धे-अर्धे", False)],
  lvl=3, bloom="Apply", fam="FA")
N("D19-0002", "D19", "An asset costs ₹10,00,000, has a residual value of ₹1,00,000 and a useful life of 9 years. What is the annual straight-line depreciation (₹)?",
  "एका मालमत्तेची किंमत ₹10,00,000, अवशिष्ट मूल्य ₹1,00,000 व उपयुक्त आयुष्य 9 वर्षे आहे. वार्षिक सरळ-रेषा घसारा किती (₹)?", 100000, lvl=3, bloom="Apply", fam="FA",
  why="(10,00,000 − 1,00,000) ÷ 9 = 1,00,000", why_mr="(10,00,000 − 1,00,000) ÷ 9 = 1,00,000")
TF("D19-0003", "D19", "An insurance premium paid in advance for next year is shown as an asset in this year's balance sheet.",
   "पुढील वर्षासाठी आगाऊ भरलेला विमा हप्ता या वर्षाच्या ताळेबंदात मालमत्ता म्हणून दाखवला जातो.", True, lvl=2, bloom="Understand", fam="FA", pool="practice")

# ====================================================================== D20 BUDGETING & CONCURRENCE
N("D20-0001", "D20",
  "A head has a BE of ₹50 crore. Spending from April to December is ₹30 crore and ₹12 crore is firmly expected from January to March. What RE (₹ crore) should be proposed?",
  "एका शीर्षाचा BE ₹50 कोटी आहे. एप्रिल ते डिसेंबर खर्च ₹30 कोटी असून जानेवारी ते मार्चमध्ये ₹12 कोटी खर्च निश्चित अपेक्षित आहे. किती RE (₹ कोटी) प्रस्तावित करावे?",
  42, lvl=3, bloom="Apply", fam="FA,PJ,PCS,COM", why="RE reflects realistic spending for the whole year: 30 + 12 = 42.", why_mr="RE संपूर्ण वर्षाचा वास्तववादी खर्च दाखवते: 30 + 12 = 42.")
S("D20-0002", "D20", "What is the difference between BE and RE?", "BE व RE मध्ये काय फरक आहे?",
  [("BE is set before the year; RE revises it during the year", "BE वर्षापूर्वी ठरते; RE वर्षादरम्यान त्यात सुधारणा करते", True),
   ("BE covers capital spending; RE covers revenue spending", "BE भांडवली खर्चासाठी; RE महसुली खर्चासाठी", False),
   ("BE is the department's estimate; RE is Finance's estimate", "BE विभागाचा अंदाज; RE वित्त विभागाचा अंदाज", False),
   ("BE is the first half-year; RE is the second half-year", "BE पहिले सहामाही; RE दुसरे सहामाही", False)],
  lvl=2, bloom="Understand", fam="FA,PJ,PCS,COM", pool="practice")
S("D20-0003", "D20", "When must a proposal be sent to Finance for concurrence?", "प्रस्ताव वित्तीय सहमतीसाठी केव्हा पाठवला पाहिजे?",
  [("Before the competent authority approves it", "सक्षम प्राधिकारी मंजूर करण्यापूर्वी", True),
   ("After approval, before the work order is issued", "मंजुरीनंतर, कार्यादेश देण्यापूर्वी", False),
   ("Only when the amount exceeds the budget provision", "फक्त रक्कम अर्थसंकल्पीय तरतुदीपेक्षा जास्त असल्यास", False),
   ("When the first bill is received for payment", "पहिले बिल देयकासाठी आल्यावर", False)],
  lvl=2, bloom="Understand", fam="FA,PJ,PCS,COM", sme=True)

# ====================================================================== D21 TAXATION
N("D21-0001", "D21", "An intra-state service invoice is ₹50,000 before tax and GST is 18%. What is the CGST amount (₹)?",
  "राज्यांतर्गत सेवा बीजक करपूर्व ₹50,000 आहे व GST 18% आहे. CGST रक्कम किती (₹)?", 4500, lvl=3, bloom="Apply", fam="FA,PCS,COM",
  why="Intra-state supply: CGST 9% + SGST 9%. 9% of 50,000 = 4,500.", why_mr="राज्यांतर्गत पुरवठा: CGST 9% + SGST 9%. 50,000 चे 9% = 4,500.")
N("D21-0002", "D21", "A works contractor (a company) submits a bill of ₹10,00,000 excluding GST. TDS under Section 194C is 2%. How much TDS (₹) is deducted?",
  "एक कंत्राटदार (कंपनी) GST वगळून ₹10,00,000 चे बिल सादर करतो. कलम 194C अंतर्गत TDS 2% आहे. किती TDS (₹) कापला जातो?", 20000,
  lvl=2, bloom="Apply", fam="FA,PCS", sme=True)
TF("D21-0003", "D21", "GST input tax credit can be claimed only on the basis of a valid tax invoice.", "GST इनपुट टॅक्स क्रेडिट फक्त वैध कर बीजकाच्या आधारेच घेता येते.", True,
   lvl=2, bloom="Understand", fam="FA,PCS", pool="practice")

# ====================================================================== D22 SERVICE RULES
S("D22-0001", "D22", "Under central government leave rules, casual leave:", "केंद्र सरकारच्या रजा नियमांनुसार नैमित्तिक रजा:",
  [("Is not a recognised leave and is not combined with others", "मान्यताप्राप्त रजा नाही व इतर रजेशी जोडली जात नाही", True),
   ("Can be carried forward to the next calendar year", "पुढील कॅलेंडर वर्षात पुढे नेता येते", False),
   ("Can be encashed along with earned leave at retirement", "निवृत्तीच्या वेळी अर्जित रजेसोबत रोखीत मिळते", False),
   ("Needs a medical certificate beyond two days", "दोन दिवसांपेक्षा जास्त असल्यास वैद्यकीय प्रमाणपत्र लागते", False)],
  lvl=2, bloom="Remember", fam="HRA,FA", sme=True, src="Maha Metro leave rules (to be validated)")
S("D22-0002", "D22", "A probationer's work has been weak but improving. The probation period ends next week. What is the usual course?",
  "एका परिविक्षाधीन कर्मचाऱ्याचे काम कमकुवत पण सुधारत आहे. परिविक्षा कालावधी पुढील आठवड्यात संपतो. सहसा काय केले जाते?",
  [("Extend probation as the rules allow, with clear targets", "नियमानुसार स्पष्ट उद्दिष्टांसह परिविक्षा वाढवणे", True),
   ("Confirm him automatically, as the period has ended", "कालावधी संपल्याने आपोआप कायम करणे", False),
   ("Terminate his service, as the work has been weak", "काम कमकुवत असल्याने सेवा समाप्त करणे", False),
   ("Transfer him to another department on probation", "परिविक्षेवर दुसऱ्या विभागात बदली करणे", False)],
  lvl=3, bloom="Apply", fam="HRA", pool="practice", sme=True)

# ====================================================================== D23 RECRUITMENT, TRAINING & PERFORMANCE
S("D23-0001", "D23", "What is a Training Needs Analysis (TNA) mainly used for?", "प्रशिक्षण गरज विश्लेषण (TNA) मुख्यतः कशासाठी वापरतात?",
  [("Finding the gap between required and current competence", "आवश्यक व सध्याच्या क्षमतेतील अंतर शोधणे", True),
   ("Fixing the training budget for each department", "प्रत्येक विभागाचा प्रशिक्षण अर्थसंकल्प ठरवणे", False),
   ("Counting training hours completed by each employee", "प्रत्येक कर्मचाऱ्याचे पूर्ण झालेले प्रशिक्षण तास मोजणे", False),
   ("Selecting trainers and venues for the annual calendar", "वार्षिक दिनदर्शिकेसाठी प्रशिक्षक व स्थळ निवडणे", False)],
  lvl=2, bloom="Understand", fam="HRA")
S("D23-0002", "D23", "Which is the best evidence that a course improved on-the-job performance?", "अभ्यासक्रमामुळे कामावरील कामगिरी सुधारली याचा सर्वोत्तम पुरावा कोणता?",
  [("Supervisor observation of the skill on the job months later", "काही महिन्यांनी कामावरील कौशल्याचे पर्यवेक्षकाचे निरीक्षण", True),
   ("High scores in the test taken at the end of the course", "अभ्यासक्रमाच्या शेवटी घेतलेल्या चाचणीतील उच्च गुण", False),
   ("Positive feedback forms filled in by participants", "सहभागींनी भरलेले सकारात्मक अभिप्राय अर्ज", False),
   ("Full attendance by all the nominated staff", "सर्व नामनिर्देशित कर्मचाऱ्यांची पूर्ण उपस्थिती", False)],
  lvl=3, bloom="Analyse", fam="HRA", why="On-the-job behaviour is Kirkpatrick level 3; an end-of-course test is level 2 and feedback forms are level 1.",
  why_mr="कामावरील वर्तन हा किर्कपॅट्रिक स्तर 3; अभ्यासक्रम-अखेरची चाचणी स्तर 2 व अभिप्राय अर्ज स्तर 1.")
S("D23-0003", "D23", "In the Kirkpatrick model, what does Level 2 measure?", "किर्कपॅट्रिक मॉडेलमध्ये स्तर 2 काय मोजतो?",
  [("Learning", "शिकणे", True), ("Reaction", "प्रतिक्रिया", False), ("Behaviour", "वर्तन", False), ("Results", "परिणाम", False)],
  lvl=2, bloom="Remember", fam="HRA", pool="practice")

# ====================================================================== D24 INDUSTRIAL RELATIONS & LABOUR LAW
M("D24-0001", "D24", "Which of these are among India's four labour codes?", "यापैकी कोणत्या भारताच्या चार कामगार संहितांपैकी आहेत?",
  [("Code on Wages", "वेतन संहिता", True), ("Industrial Relations Code", "औद्योगिक संबंध संहिता", True),
   ("Occupational Safety, Health and Working Conditions Code", "व्यावसायिक सुरक्षा, आरोग्य व कामाच्या परिस्थितीची संहिता", True),
   ("Code on Social Security", "सामाजिक सुरक्षा संहिता", True), ("Code on Contract Labour Licensing", "कंत्राटी कामगार परवाना संहिता", False)],
  lvl=2, bloom="Remember", fam="HRA,PCS")
TF("D24-0002", "D24", "If a contractor fails to pay wages to contract labour, the principal employer is liable to pay them.", "कंत्राटदाराने कंत्राटी कामगारांचे वेतन न दिल्यास मुख्य नियोक्त्याला ते द्यावे लागते.",
   True, lvl=2, bloom="Understand", fam="HRA,PCS")
J("D24-0003", "D24", "Contract cleaning staff at a station complain to you that their wages for last month have not been paid. What do you do?",
  "स्थानकावरील कंत्राटी स्वच्छता कर्मचारी तुमच्याकडे तक्रार करतात की मागील महिन्याचे वेतन मिळालेले नाही. तुम्ही काय कराल?",
  [("Record it and refer it to the department managing the contract", "नोंद करा व करार हाताळणाऱ्या विभागाकडे पाठवा", 1.0),
   ("Tell them it is between them and their contractor", "हा त्यांचा व त्यांच्या कंत्राटदाराचा प्रश्न आहे असे सांगा", 0.0),
   ("Call the contractor's supervisor and ask him to pay quickly", "कंत्राटदाराच्या पर्यवेक्षकाला फोन करून लवकर पैसे देण्यास सांगा", 0.4),
   ("Advise them to stop work until wages are paid", "वेतन मिळेपर्यंत काम थांबवण्याचा सल्ला द्या", 0.0)],
  lvl=3, fam="HRA,PCS,SC", pool="practice")

# ====================================================================== D25 PUBLIC PROCUREMENT
S("D25-0001", "D25",
  "A department plans to split a requirement into several smaller purchase orders so that each falls within a lower approval limit. This is:",
  "एक विभाग एकच गरज अनेक लहान खरेदी आदेशांमध्ये विभागण्याचे ठरवतो जेणेकरून प्रत्येक आदेश कमी मंजुरी मर्यादेत येईल. हे:",
  [("Not allowed – splitting to avoid sanction is prohibited", "परवानगी नाही – मंजुरी टाळण्यासाठी विभागणी निषिद्ध आहे", True),
   ("Allowed, if the total stays within the annual budget", "एकूण रक्कम वार्षिक अर्थसंकल्पात असल्यास परवानगी आहे", False),
   ("Allowed, if the requirement is certified as urgent", "गरज तातडीची म्हणून प्रमाणित असल्यास परवानगी आहे", False),
   ("Allowed, if each order is placed through GeM", "प्रत्येक आदेश GeM द्वारे दिल्यास परवानगी आहे", False)],
  lvl=3, fam="FA,PCS", src="GFR 2017; CVC guidelines")
S("D25-0002", "D25",
  "An item you need is listed on GeM by several sellers. A local supplier, not on GeM, offers a lower price by email. What is the correct approach?",
  "तुम्हाला हवी असलेली वस्तू GeM वर अनेक विक्रेत्यांकडे उपलब्ध आहे. GeM वर नसलेला स्थानिक पुरवठादार ई-मेलने कमी किंमत देतो. योग्य पद्धत कोणती?",
  [("Buy through GeM, using its bid or reverse-auction option", "GeM मार्फत, त्याचा बोली किंवा रिव्हर्स-लिलाव पर्याय वापरून खरेदी करा", True),
   ("Buy from the local supplier, as the price is lower", "किंमत कमी असल्याने स्थानिक पुरवठादाराकडून खरेदी करा", False),
   ("Call quotations from three local suppliers outside GeM", "GeM बाहेर तीन स्थानिक पुरवठादारांकडून दरपत्रके मागवा", False),
   ("Ask the local supplier to match the lowest GeM price", "स्थानिक पुरवठादाराला सर्वात कमी GeM किमतीशी जुळवण्यास सांगा", False)],
  lvl=3, bloom="Apply", fam="FA,PCS", sme=True, src="GFR 2017 Rule 149; Maha Metro procurement manual (applicability to be confirmed)")
S("D25-0003", "D25", "In bid evaluation, 'L1' means:", "निविदा मूल्यमापनात 'L1' म्हणजे:",
  [("The lowest evaluated responsive bid", "सर्वात कमी मूल्यमापित पात्र निविदा", True), ("The lowest quoted bid, before evaluation", "मूल्यमापनापूर्वीची सर्वात कमी नमूद निविदा", False),
   ("The first bid to be opened", "उघडलेली पहिली निविदा", False), ("The bidder with the best technical score", "सर्वोत्तम तांत्रिक गुण असलेला निविदाकार", False)],
  lvl=2, bloom="Understand", fam="FA,PCS", pool="practice")

# ====================================================================== D26 CONTRACT MANAGEMENT
S("D26-0001", "D26", "A contractor's work is delayed 30 days because the employer handed over the site late. What is the contractor normally entitled to?",
  "नियोक्त्याने साइट उशिरा दिल्याने कंत्राटदाराचे काम 30 दिवस उशिरा होते. कंत्राटदाराला सामान्यतः कशाचा हक्क असतो?",
  [("An extension of time without liquidated damages", "नुकसानभरपाईशिवाय मुदतवाढ", True),
   ("Waiver of damages, but no change to the completion date", "नुकसानभरपाई माफ, पण पूर्णत्वाची तारीख तीच", False),
   ("Nothing, as delays are the contractor's risk", "काहीच नाही, कारण विलंब ही कंत्राटदाराची जोखीम आहे", False),
   ("Termination of the contract with full payment", "पूर्ण देयकासह करार समाप्ती", False)],
  lvl=3, bloom="Apply", fam="PCS,PJ,FA")
S("D26-0002", "D26", "When is a variation order issued?", "बदल आदेश (variation order) केव्हा दिला जातो?",
  [("When the scope of work under the contract changes", "करारातील कामाची व्याप्ती बदलल्यावर", True), ("When the contract price is revised for inflation", "महागाईनुसार कराराची किंमत सुधारल्यावर", False),
   ("When the completion date is extended", "पूर्णत्वाची तारीख वाढवल्यावर", False), ("When a bill is held for deductions", "वजावटीसाठी बिल रोखल्यावर", False)],
  lvl=2, bloom="Understand", fam="PCS,PJ,FA", pool="practice")

# ====================================================================== D27 INVENTORY & STORES
N("D27-0001", "D27", "Annual demand is 1,200 units, ordering cost is ₹100 per order and holding cost is ₹24 per unit per year. What is the EOQ (units)?",
  "वार्षिक मागणी 1,200 नग, प्रति ऑर्डर खर्च ₹100 व धारण खर्च ₹24 प्रति नग प्रति वर्ष आहे. EOQ किती (नग)?", 100, lvl=3, bloom="Apply", fam="PCS",
  why="EOQ = √(2DS/H) = √(2×1200×100/24) = 100", why_mr="EOQ = √(2DS/H) = √(2×1200×100/24) = 100")
S("D27-0002", "D27", "A spare has low annual consumption value but its absence would stop trains. How should it be managed?",
  "एका सुट्या भागाचे वार्षिक वापर मूल्य कमी आहे, पण तो नसल्यास गाड्या थांबतील. त्याचे व्यवस्थापन कसे करावे?",
  [("As a vital (VED) item with safety stock, though 'C' in ABC", "ABC मध्ये 'C' असला तरी सुरक्षा साठ्यासह महत्त्वाची (VED) वस्तू म्हणून", True),
   ("As a 'C' item, ordered only when stock runs out", "'C' वस्तू म्हणून, साठा संपल्यावरच मागवून", False),
   ("As an 'A' item, reviewed and counted every week", "'A' वस्तू म्हणून, दर आठवड्याला आढावा व मोजणी करून", False),
   ("As a non-stock item, bought from the market when needed", "साठा न ठेवता, गरज पडल्यावर बाजारातून खरेदी करून", False)],
  lvl=4, bloom="Analyse", fam="PCS,RS,STA")
S("D27-0003", "D27", "Under FIFO issue of stores:", "FIFO पद्धतीने भांडार वस्तू देताना:",
  [("The oldest stock is issued first", "सर्वात जुना साठा आधी दिला जातो", True), ("The newest stock is issued first", "सर्वात नवा साठा आधी दिला जातो", False),
   ("The cheapest stock is issued first", "सर्वात स्वस्त साठा आधी दिला जातो", False), ("The nearest stock is issued first", "सर्वात जवळचा साठा आधी दिला जातो", False)],
  lvl=1, bloom="Remember", fam="PCS", pool="practice")

# ====================================================================== D28 COMMERCIAL & PROPERTY DEVELOPMENT
M("D28-0001", "D28", "Which of these are non-fare revenue for a metro?", "यापैकी मेट्रोसाठी भाडेतर महसूल कोणते?",
  [("Advertising on stations and trains", "स्थानके व गाड्यांवरील जाहिरात", True), ("Leasing of retail space", "किरकोळ जागा भाड्याने देणे", True),
   ("Station naming rights", "स्थानक नामकरण हक्क", True), ("Sale of smart cards with stored value", "मूल्य साठवलेल्या स्मार्ट कार्डची विक्री", False),
   ("Penalties collected for ticketless travel", "विनातिकीट प्रवासासाठी वसूल दंड", False)],
  lvl=2, bloom="Understand", fam="COM,FA")
S("D28-0002", "D28", "What does Transit Oriented Development (TOD) aim to do?", "ट्रान्झिट ओरिएंटेड डेव्हलपमेंट (TOD) चे उद्दिष्ट काय आहे?",
  [("Concentrate homes and jobs within walking distance of stations", "स्थानकांपासून चालण्याच्या अंतरात घरे व रोजगार केंद्रित करणे", True),
   ("Build large multi-level car parks at every metro station", "प्रत्येक मेट्रो स्थानकावर मोठी बहुमजली वाहनतळे बांधणे", False),
   ("Move offices away from congested city centres to suburbs", "गर्दीच्या शहर-केंद्रांपासून कार्यालये उपनगरांत हलवणे", False),
   ("Reserve land along the line for future tracks", "भविष्यातील रुळांसाठी मार्गालगत जमीन राखून ठेवणे", False)],
  lvl=2, bloom="Understand", fam="COM,PJ", pool="practice")

# ====================================================================== D29 IT & CYBERSECURITY
S("D29-0001", "D29", "A vendor asks for urgent remote desktop access to a SCADA server from the internet to fix a bug. What is the right approach?",
  "एक विक्रेता बग दुरुस्त करण्यासाठी इंटरनेटवरून SCADA सर्व्हरचा तातडीचा रिमोट डेस्कटॉप प्रवेश मागतो. योग्य पद्धत कोणती?",
  [("Give approved, time-limited access via the jump server, then revoke", "जंप सर्व्हरद्वारे मंजूर, मर्यादित-वेळेचा प्रवेश द्या, मग रद्द करा", True),
   ("Open the RDP port on the firewall for one day only", "फायरवॉलवर RDP पोर्ट फक्त एका दिवसासाठी उघडा", False),
   ("Share the admin password by phone and change it afterwards", "फोनवर ॲडमिन पासवर्ड सांगा व नंतर बदला", False),
   ("Refuse all remote support for operational systems permanently", "परिचालन प्रणालींसाठी सर्व दूरस्थ मदत कायमची नाकारा", False)],
  lvl=3, bloom="Apply", fam="IT,STA,OCC")
M("D29-0002", "D29", "The CIA triad of information security consists of:", "माहिती सुरक्षेच्या CIA त्रयीमध्ये काय येते?",
  [("Confidentiality", "गोपनीयता", True), ("Integrity", "अखंडता", True), ("Availability", "उपलब्धता", True),
   ("Authentication", "प्रमाणीकरण", False), ("Accountability", "उत्तरदायित्व", False)], lvl=2, bloom="Remember", fam="IT")
S("D29-0003", "D29", "What does the 3-2-1 backup rule mean?", "3-2-1 बॅकअप नियम म्हणजे काय?",
  [("3 copies of data, on 2 types of media, 1 kept off-site", "डेटाच्या 3 प्रती, 2 प्रकारच्या माध्यमांवर, 1 बाहेरील ठिकाणी", True),
   ("3 daily backups, 2 weekly backups, 1 monthly backup", "3 दैनिक, 2 साप्ताहिक व 1 मासिक बॅकअप", False),
   ("3 servers, 2 data centres, 1 cloud provider", "3 सर्व्हर, 2 डेटा सेंटर, 1 क्लाउड पुरवठादार", False),
   ("Restore tests every 3 months, 2 people, 1 report", "दर 3 महिन्यांनी पुनर्स्थापना चाचणी, 2 व्यक्ती, 1 अहवाल", False)],
  lvl=2, bloom="Understand", fam="IT", pool="practice")

# ====================================================================== D30 OFFICE PROCEDURE, NOTING & RTI
N("D30-0001", "D30", "Under the RTI Act, 2005, within how many days must information normally be provided to the applicant?",
  "माहिती अधिकार अधिनियम, 2005 नुसार अर्जदाराला सामान्यतः किती दिवसांत माहिती द्यावी लागते?", 30, lvl=1, bloom="Remember",
  fam="HRA,FA,PCS,COM,IT,CORP,PJ", src="RTI Act 2005, Section 7")
F("D30-0002", "D30", "Under the RTI Act, information concerning the life or liberty of a person must be provided within ____ hours.",
  "माहिती अधिकार अधिनियमानुसार एखाद्या व्यक्तीच्या जीवन किंवा स्वातंत्र्याशी संबंधित माहिती ____ तासांत द्यावी लागते.", ["48", "48 hours", "48 तास", "४८"],
  lvl=2, bloom="Remember", fam="HRA,CORP,COM,FA,PCS,IT,PJ")
S("D30-0003", "D30", "An RTI application asks for information held by another public authority. What should the PIO do?",
  "माहिती अधिकार अर्जात दुसऱ्या सार्वजनिक प्राधिकरणाकडे असलेली माहिती मागितली आहे. जन माहिती अधिकाऱ्याने काय करावे?",
  [("Transfer it to that authority within 5 days and inform the applicant", "5 दिवसांत तो अर्ज त्या प्राधिकरणाकडे पाठवा व अर्जदाराला कळवा", True),
   ("Reject it, as the information is not held by this office", "माहिती या कार्यालयाकडे नसल्याने अर्ज फेटाळा", False),
   ("Return the fee and ask the applicant to apply afresh", "शुल्क परत करा व नव्याने अर्ज करण्यास सांगा", False),
   ("Collect the information from that authority and reply within 30 days", "त्या प्राधिकरणाकडून माहिती मिळवून 30 दिवसांत उत्तर द्या", False)],
  lvl=3, bloom="Apply", fam="HRA,CORP,COM,FA,PCS,IT", src="RTI Act 2005, Section 6(3)")
W("D30-0004", "D30",
  "Draft a short note (6–10 lines) on file seeking approval to conduct a one-day fire safety refresher for 30 station staff at the CoE.",
  "CoE मध्ये 30 स्थानक कर्मचाऱ्यांसाठी एक दिवसीय अग्निसुरक्षा उजळणी प्रशिक्षण घेण्यासाठी मंजुरी मागणारी छोटी टिप्पणी (6–10 ओळी) फाइलवर लिहा.",
  "1 mark each: subject and reference; need/justification; details (date, participants, faculty, venue); financial implication and budget head; specific approval sought.",
  "प्रत्येकी 1 गुण: विषय व संदर्भ; गरज/औचित्य; तपशील (तारीख, सहभागी, प्रशिक्षक, स्थळ); आर्थिक परिणाम व अर्थसंकल्प शीर्ष; नेमकी मागितलेली मंजुरी.",
  marks=5, lvl=3, fam="HRA,FA,PCS,COM,IT,CORP")

# ====================================================================== D31 LEGAL, VIGILANCE & GOVERNANCE
S("D31-0001", "D31", "Who approves the annual financial statements of a company before they are placed before the shareholders?",
  "भागधारकांसमोर ठेवण्यापूर्वी कंपनीची वार्षिक वित्तीय विवरणपत्रे कोण मंजूर करते?",
  [("The Board of Directors", "संचालक मंडळ", True), ("The statutory auditor", "वैधानिक लेखापरीक्षक", False),
   ("The audit committee alone", "फक्त लेखापरीक्षा समिती", False), ("The company secretary", "कंपनी सचिव", False)],
  lvl=2, bloom="Remember", fam="CORP,FA")
TF("D31-0002", "D31", "Anonymous complaints sent to the vigilance department are always investigated.", "दक्षता विभागाकडे आलेल्या निनावी तक्रारींची नेहमीच चौकशी केली जाते.", False,
   lvl=2, bloom="Understand", fam="CORP,FA,PCS,HRA", pool="practice",
   why="As per CVC guidelines, anonymous or pseudonymous complaints are generally not acted upon.", why_mr="CVC मार्गदर्शक सूचनांनुसार निनावी किंवा टोपणनावाने आलेल्या तक्रारींवर सामान्यतः कारवाई होत नाही.")
S("D31-0003", "D31", "A legal notice from a contractor arrives at your section. What should you do first?", "कंत्राटदाराची कायदेशीर नोटीस तुमच्या विभागात येते. तुम्ही प्रथम काय कराल?",
  [("Diarise it and send it to the legal cell with facts and deadline", "आवक नोंद करा व तथ्ये व मुदतीसह विधी कक्षाकडे पाठवा", True),
   ("Reply to the contractor directly, as you know the facts", "तथ्ये माहीत असल्याने कंत्राटदाराला थेट उत्तर द्या", False),
   ("Keep it on file until the contractor actually goes to court", "कंत्राटदार प्रत्यक्ष न्यायालयात जाईपर्यंत फाइलमध्ये ठेवा", False),
   ("Forward it to the contractor's engineer for comments", "टिप्पणीसाठी कंत्राटदाराच्या अभियंत्याकडे पाठवा", False)],
  lvl=2, bloom="Apply", fam="CORP,PCS,FA,PJ")


# v1.3: explanations kept in seed/explanations.py
from .explanations import SRC as _SRC, WHY as _WHY  # noqa: E402
for _d in ITEMS:
    if not _d["why"] and _d["code"] in _WHY:
        _d["why"], _d["why_mr"] = _WHY[_d["code"]]
    if not _d["src"] and _d["code"] in _SRC:
        _d["src"] = _SRC[_d["code"]]
