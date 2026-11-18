-- Step 19: Training catalogue and Individual Development Plans (Goal 6)
CREATE TABLE intervention (
  id              BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
  competency_code VARCHAR(8)    NULL,
  category        VARCHAR(20)   NULL,
  gap_type        VARCHAR(20)   NOT NULL,
  title_en        VARCHAR(300)  NOT NULL,
  title_mr        VARCHAR(400)  NOT NULL DEFAULT '',
  provider        VARCHAR(60)   NOT NULL DEFAULT 'CoE',
  mode            VARCHAR(40)   NOT NULL DEFAULT 'Classroom',
  duration_hours  DECIMAL(5,2)  NOT NULL DEFAULT 4.00,
  link            VARCHAR(300)  NOT NULL DEFAULT '',
  created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_intv_comp FOREIGN KEY (competency_code) REFERENCES competency (code)
);

CREATE TABLE development_plan (
  id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  employee_id      BIGINT       NOT NULL,
  cycle            VARCHAR(20)  NOT NULL DEFAULT 'T0',
  readiness        VARCHAR(20)  NOT NULL DEFAULT 'Amber',
  competency_index DECIMAL(5,2) NOT NULL DEFAULT 0.00,
  status           VARCHAR(20)  NOT NULL DEFAULT 'proposed',
  confirmed_by     VARCHAR(50)  NOT NULL DEFAULT '',
  confirmed_at     DATETIME(6)  NULL,
  created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  CONSTRAINT fk_dp_employee FOREIGN KEY (employee_id) REFERENCES employee (id)
);
CREATE INDEX ix_dp_emp_cycle ON development_plan (employee_id, cycle);

CREATE TABLE development_plan_item (
  id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
  plan_id         BIGINT       NOT NULL,
  competency_code VARCHAR(8)   NOT NULL,
  required_level  INT          NOT NULL,
  attained_level  INT          NOT NULL,
  score           DECIMAL(5,2) NOT NULL DEFAULT 0.00,
  gap             INT          NOT NULL,
  priority        INT          NOT NULL,
  gap_type        VARCHAR(20)  NOT NULL,
  intervention_id BIGINT       NULL,
  target_date     DATETIME(6)  NULL,
  status          VARCHAR(20)  NOT NULL DEFAULT 'open',
  CONSTRAINT fk_dpi_plan FOREIGN KEY (plan_id) REFERENCES development_plan (id) ON DELETE CASCADE,
  CONSTRAINT fk_dpi_comp FOREIGN KEY (competency_code) REFERENCES competency (code),
  CONSTRAINT fk_dpi_intervention FOREIGN KEY (intervention_id) REFERENCES intervention (id)
);
CREATE INDEX ix_dpi_plan_prio ON development_plan_item (plan_id, priority);

-- Seed standard training interventions catalogue from UTKARSH CoE, simulators, labs and iGOT Karmayogi
INSERT INTO intervention (competency_code, category, gap_type, title_en, title_mr, provider, mode, duration_hours) VALUES
  ('C01', NULL, 'safety', 'Fire safety & emergency response refresher with live extinguisher drill (CoE)', 'अग्निसुरक्षा व आपत्कालीन प्रतिसाद उजळणी, प्रत्यक्ष अग्निशामक सरावासह (CoE)', 'CoE', 'Classroom', 8.0),
  ('D01', NULL, 'safety', 'GR/SR refresher and oral board with Traffic/Safety officer', 'GR/SR उजळणी व वाहतूक/सुरक्षा अधिकाऱ्यासमोर मौखिक परीक्षा', 'CoE', 'Classroom', 12.0),
  ('D02', NULL, 'safety', 'Rolling Stock Simulator: degraded-mode driving and fault handling', 'रोलिंग स्टॉक सिम्युलेटर: बिघाड-मोड चालन व दोष हाताळणी', 'Simulator', 'Simulator', 8.0),
  ('D03', NULL, 'safety', 'SCR operations practical: PA/PIDS, CCTV, ESP, opening/closing', 'SCR परिचालन प्रात्यक्षिक: PA/PIDS, CCTV, ESP, उघडणे/बंद करणे', 'CoE Lab', 'Lab practical', 6.0),
  ('D04', NULL, 'safety', 'OCC simulator: headway regulation and service restoration', 'OCC सिम्युलेटर: हेडवे नियमन व सेवा पुनर्स्थापना', 'Simulator', 'Simulator', 8.0),
  ('D05', NULL, 'safety', 'Emergency & degraded working drill (evacuation, fire, single-line working)', 'आपत्कालीन व बिघाडकालीन कार्य सराव (बाहेर काढणे, आग, एकेरी मार्ग)', 'CoE', 'Workshop', 8.0),
  ('D06', NULL, 'major', 'AFC Lab: gates, TOM, card/QR fault handling and revenue reconciliation', 'AFC लॅब: गेट, TOM, कार्ड/QR दोष हाताळणी व महसूल ताळमेळ', 'CoE Lab', 'Lab practical', 6.0),
  ('D07', NULL, 'safety', 'RS Lab + Simulator: TCMS/DMI faults, brakes, doors, pantograph', 'RS लॅब + सिम्युलेटर: TCMS/DMI दोष, ब्रेक, दरवाजे, पॅन्टोग्राफ', 'CoE Lab', 'Lab practical', 12.0),
  ('D08', NULL, 'safety', 'Signalling Lab: ATC/ATS/CBI fault diagnosis and PSD controller', 'सिग्नलिंग लॅब: ATC/ATS/CBI दोष निदान व PSD नियंत्रक', 'CoE Lab', 'Lab practical', 12.0),
  ('D09', NULL, 'major', 'Telecom Lab: PA/PIDS, CCTV, radio and SCADA practice', 'दूरसंचार लॅब: PA/PIDS, CCTV, रेडिओ व SCADA सराव', 'CoE Lab', 'Lab practical', 8.0),
  ('D10', NULL, 'safety', 'PS & Traction Lab: OHE, switchgear, protection, SCADA switching, earthing', 'PS व ट्रॅक्शन लॅब: OHE, स्विचगियर, संरक्षण, SCADA स्विचिंग, अर्थिंग', 'CoE Lab', 'Lab practical', 12.0),
  ('D11', NULL, 'safety', 'Lift & Escalator Simulator: passenger rescue and fault finding; E&M Lab (HVAC, fire, BMS)', 'लिफ्ट व एस्केलेटर सिम्युलेटर: प्रवासी सुटका व दोष शोध; E&M लॅब (HVAC, आग, BMS)', 'Simulator', 'Simulator', 8.0),
  ('D12', NULL, 'safety', 'Track Lab: geometry, fastenings, turnouts and inspection practice', 'ट्रॅक लॅब: भूमिती, फास्टनिंग, टर्नआउट व तपासणी सराव', 'CoE Lab', 'Lab practical', 8.0),
  ('D13', NULL, 'safety', 'Permit-to-work, isolation, test-for-dead and earthing practical (PS & Traction Lab)', 'कार्य-परवाना, विलगीकरण, मृत-चाचणी व अर्थिंग प्रात्यक्षिक (PS व ट्रॅक्शन लॅब)', 'CoE Lab', 'Lab practical', 6.0),
  ('D18', NULL, 'safety', 'Hazard identification, risk assessment and incident investigation workshop', 'धोका ओळख, जोखीम मूल्यांकन व घटना तपास कार्यशाळा', 'CoE', 'Workshop', 8.0),
  ('D25', NULL, 'major', 'Public procurement: GFR 2017, CVC guidelines, GeM and e-tendering', 'सार्वजनिक खरेदी: GFR 2017, CVC मार्गदर्शक सूचना, GeM व ई-निविदा', 'iGOT Karmayogi', 'E-learning', 6.0),
  ('D30', NULL, 'minor', 'e-Office noting and drafting clinic with RTI reply practice', 'ई-ऑफिस टिप्पणी व मसुदा लेखन सत्र, RTI उत्तर सरावासह', 'CoE', 'Workshop', 4.0),
  ('C05', NULL, 'minor', 'Cyber hygiene micro-module and phishing simulation', 'सायबर स्वच्छता लघु-मॉड्यूल व फिशिंग सिम्युलेशन', 'iGOT Karmayogi', 'E-learning', 2.0),
  (NULL, 'Domain', 'safety', 'Mandatory refresher at CoE (classroom + simulator/practical) within 30 days; re-assess before independent duty', '30 दिवसांत CoE मध्ये अनिवार्य उजळणी (वर्ग + सिम्युलेटर/प्रात्यक्षिक); स्वतंत्र कामापूर्वी पुनर्मूल्यांकन', 'CoE', 'Classroom', 12.0),
  (NULL, 'Core', 'safety', 'Mandatory safety refresher at CoE within 30 days', '30 दिवसांत CoE मध्ये अनिवार्य सुरक्षा उजळणी', 'CoE', 'Classroom', 8.0),
  (NULL, 'Domain', 'major', 'Structured course (CoE or OEM) followed by mentored OJT with checklist', 'संरचित अभ्यासक्रम (CoE किंवा OEM) व त्यानंतर तपासणी-सूचीसह मार्गदर्शित OJT', 'CoE', 'Classroom', 16.0),
  (NULL, 'Domain', 'minor', 'Targeted e-learning modules, toolbox talks and OJT tasks', 'लक्ष्यित ई-लर्निंग मॉड्यूल, टूलबॉक्स चर्चा व OJT कामे', 'OJT', 'OJT with checklist', 6.0),
  (NULL, 'Core', 'major', 'CoE core-skills course with practice assignments', 'सराव असाइनमेंटसह CoE मूलभूत-कौशल्य अभ्यासक्रम', 'CoE', 'Classroom', 8.0),
  (NULL, 'Core', 'minor', 'iGOT Karmayogi module and supervisor coaching', 'iGOT कर्मयोगी मॉड्यूल व पर्यवेक्षकाचे मार्गदर्शन', 'iGOT Karmayogi', 'E-learning', 4.0),
  (NULL, 'Behavioural', 'behavioural', 'Behavioural workshop, coaching and iGOT Karmayogi behavioural modules', 'वर्तनविषयक कार्यशाळा, मार्गदर्शन व iGOT कर्मयोगी वर्तनविषयक मॉड्यूल', 'iGOT Karmayogi', 'Workshop', 8.0),
  (NULL, NULL, 'strength', 'Nominate as SME item author, OJT mentor or internal trainer; consider for multi-skilling', 'SME प्रश्नलेखक, OJT मार्गदर्शक किंवा अंतर्गत प्रशिक्षक म्हणून नामनिर्देशन; बहुकौशल्यासाठी विचार', 'CoE', 'Mentoring', 0.0);
