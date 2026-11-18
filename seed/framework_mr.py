"""Marathi names and reference data that complement framework_extract.json
(extracted from MahaMetro_Competency_Framework_and_Tracker.xlsx v0.1, 19 Oct 2026).

Marathi translations are working translations prepared for the platform and must be
checked by the CoE language editor before go-live.
"""

COMPETENCY_MR = {
    "C01": "सुरक्षा संस्कृती व आपत्कालीन प्रतिसाद",
    "C02": "मेट्रो प्रणालीची ओळख",
    "C03": "ग्राहकाभिमुखता व प्रवासी सेवा",
    "C04": "सचोटी, नीतिमत्ता व दक्षता जागरूकता",
    "C05": "डिजिटल साक्षरता व सायबर स्वच्छता",
    "C06": "संवाद (मौखिक व लेखी, द्विभाषिक)",
    "B01": "सांघिक कार्य व सहकार्य",
    "B02": "समस्या निराकरण व निर्णयक्षमता",
    "B03": "दबावाखाली संयम",
    "B04": "जबाबदारी व उत्तरदायित्व",
    "B05": "नेतृत्व व मनुष्यबळ विकास",
    "B06": "नियोजन व संघटन",
    "B07": "भागधारक व्यवस्थापन",
    "D01": "सामान्य व विशेष नियम आणि परिचालन कार्यपद्धती",
    "D02": "रेल्वे चालन व हाताळणी (CBTC मोड)",
    "D03": "स्थानक परिचालन व उपकरणे",
    "D04": "वाहतूक नियंत्रण व सेवा नियमन",
    "D05": "बिघाडकालीन कार्यपद्धती व आपत्कालीन प्रक्रिया",
    "D06": "AFC, तिकीट प्रणाली व महसूल हाताळणी",
    "D07": "रोलिंग स्टॉक प्रणाली",
    "D08": "सिग्नलिंग व ट्रेन नियंत्रण",
    "D09": "दूरसंचार, SCADA व संप्रेषण प्रणाली",
    "D10": "ट्रॅक्शन वीजपुरवठा (RSS, OHE)",
    "D11": "E&M इमारत प्रणाली",
    "D12": "स्थापत्य, रूळमार्ग व संरचना",
    "D13": "विद्युत सुरक्षा व कार्य-परवानगी (Permit-to-Work)",
    "D14": "देखभाल व्यवस्थापन व RAMS",
    "D15": "आरेखन व अभियांत्रिकी पुनरावलोकन",
    "D16": "प्रकल्प नियोजन व संनियंत्रण",
    "D17": "QA/QC व बांधकाम सुरक्षा",
    "D18": "सुरक्षा व्यवस्थापन प्रणाली व जोखीम मूल्यांकन",
    "D19": "लेखा व वित्तीय अहवाल",
    "D20": "अर्थसंकल्प, खर्च निर्धारण व वित्तीय सहमती",
    "D21": "कर आकारणी व लेखापरीक्षा अनुपालन",
    "D22": "सेवा नियम, आस्थापना व वेतन",
    "D23": "भरती, प्रशिक्षण व कामगिरी व्यवस्थापन",
    "D24": "औद्योगिक संबंध व कामगार कायदा अनुपालन",
    "D25": "सार्वजनिक खरेदी (GFR, CVC, GeM, ई-निविदा)",
    "D26": "करार व्यवस्थापन व दावे",
    "D27": "साठा व भांडार व्यवस्थापन",
    "D28": "व्यावसायिक महसूल व मालमत्ता विकास",
    "D29": "IT पायाभूत सुविधा, अनुप्रयोग व सायबर सुरक्षा",
    "D30": "कार्यालयीन कार्यपद्धती, टिप्पणी, मसुदा लेखन व RTI",
    "D31": "विधी, दक्षता व कॉर्पोरेट प्रशासन",
}

# code, name_en, name_mr, group (used for composite weights)
ROLE_FAMILIES = [
    ("TO", "Train Operations", "रेल्वे परिचालन", "OPS"),
    ("SC", "Station Operations", "स्थानक परिचालन", "OPS"),
    ("OCC", "Traffic Control (OCC/DCC)", "वाहतूक नियंत्रण (OCC/DCC)", "OPS"),
    ("RS", "Rolling Stock", "रोलिंग स्टॉक", "MNT"),
    ("STA", "Signalling, Telecom & AFC", "सिग्नलिंग, दूरसंचार व AFC", "MNT"),
    ("TPS", "Traction & Power Supply", "ट्रॅक्शन व वीजपुरवठा", "MNT"),
    ("EM", "Electrical & Mechanical (E&M)", "विद्युत व यांत्रिक (E&M)", "MNT"),
    ("CT", "Civil, Track & Structures", "स्थापत्य, रूळमार्ग व संरचना", "MNT"),
    ("PJ", "Projects, Design & Planning", "प्रकल्प, आरेखन व नियोजन", "PJS"),
    ("SFS", "Safety & Security", "सुरक्षा व संरक्षण", "PJS"),
    ("FA", "Finance & Accounts", "वित्त व लेखा", "NT"),
    ("HRA", "HR & Administration", "मानव संसाधन व प्रशासन", "NT"),
    ("PCS", "Procurement, Contracts & Stores", "खरेदी, करार व भांडार", "NT"),
    ("COM", "Commercial & Property Development", "व्यावसायिक व मालमत्ता विकास", "NT"),
    ("IT", "IT & Digital", "माहिती तंत्रज्ञान व डिजिटल", "NT"),
    ("CORP", "Corporate Services", "कॉर्पोरेट सेवा", "NT"),
]

# code, band_en, band_mr, core, behavioural, domain offsets
GRADES = [
    ("G1", "Operative", "कार्यकारी कर्मचारी", 0, -1, -1),
    ("G2", "Supervisory", "पर्यवेक्षकीय", 0, 0, 0),
    ("G3", "Junior Executive", "कनिष्ठ अधिकारी", 0, 0, 0),
    ("G4", "Middle Management", "मध्यम व्यवस्थापन", 0, 1, 1),
    ("G5", "Senior Management", "वरिष्ठ व्यवस्थापन", 0, 1, 0),
]

# level, min score, label_en, label_mr
CUT_SCORES = [
    (1, 0, "Aware", "जाणीव"),
    (2, 40, "Basic", "प्राथमिक"),
    (3, 55, "Competent", "सक्षम"),
    (4, 70, "Advanced", "प्रगत"),
    (5, 85, "Expert", "तज्ज्ञ"),
]

# Composite weights by role group (design note section 5.2): knowledge, sjt, practical, rating
WEIGHTS = {
    "OPS": {"knowledge": 35, "sjt": 20, "practical": 35, "rating": 10},
    "MNT": {"knowledge": 40, "sjt": 15, "practical": 35, "rating": 10},
    "PJS": {"knowledge": 45, "sjt": 30, "practical": 10, "rating": 15},
    "NT": {"knowledge": 45, "sjt": 30, "practical": 10, "rating": 15},
    "BEHAVIOURAL": {"knowledge": 0, "sjt": 60, "practical": 0, "rating": 40},
}

# Typical designations -> role family / grade, used to pre-fill the mapping table
DESIGNATION_MAP = [
    ("Train Operator", "TO", "G2"), ("Crew Controller", "TO", "G2"),
    ("Station Controller", "SC", "G2"), ("Customer Relations Assistant", "SC", "G1"),
    ("Station Manager", "SC", "G3"), ("Traffic Controller", "OCC", "G2"),
    ("Chief Controller", "OCC", "G3"), ("Depot Controller", "OCC", "G2"),
    ("Power Controller", "OCC", "G2"),
    ("Technician (Rolling Stock)", "RS", "G1"), ("Junior Engineer (Rolling Stock)", "RS", "G2"),
    ("Section Engineer (Rolling Stock)", "RS", "G2"), ("Assistant Manager (Rolling Stock)", "RS", "G3"),
    ("Technician (Signalling)", "STA", "G1"), ("Technician (Telecom)", "STA", "G1"),
    ("Junior Engineer (Signalling)", "STA", "G2"), ("Section Engineer (Telecom)", "STA", "G2"),
    ("Technician (Traction)", "TPS", "G1"), ("Section Engineer (Traction)", "TPS", "G2"),
    ("Technician (E&M)", "EM", "G1"), ("Section Engineer (E&M)", "EM", "G2"),
    ("Technician (Track)", "CT", "G1"), ("Section Engineer (Civil)", "CT", "G2"),
    ("Assistant Manager (Civil)", "CT", "G3"), ("Planning Engineer", "PJ", "G3"),
    ("Safety Officer", "SFS", "G3"), ("Fire Officer", "SFS", "G3"), ("Security Supervisor", "SFS", "G2"),
    ("Accounts Assistant", "FA", "G1"), ("Assistant Manager (Finance)", "FA", "G3"),
    ("Manager (Finance)", "FA", "G3"), ("Office Assistant", "HRA", "G1"),
    ("Assistant Manager (HR)", "HRA", "G3"), ("Stores Assistant", "PCS", "G1"),
    ("Manager (Contracts)", "PCS", "G3"), ("Commercial Assistant", "COM", "G1"),
    ("IT Assistant", "IT", "G1"), ("Assistant Manager (IT)", "IT", "G3"),
    ("Deputy General Manager", None, "G4"), ("Joint General Manager", None, "G4"),
    ("Additional General Manager", None, "G4"), ("General Manager", None, "G5"),
]
