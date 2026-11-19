"""Gera a base FICTÍCIA de demonstração (valores em R$ mil). Semente fixa = sempre igual."""
import random, sys, os
from decimal import Decimal, ROUND_HALF_UP

destino = sys.argv[1]
random.seed(2026)
CORTE = (2026, 9)
ANOS = [2024, 2025, 2026, 2027]
CRESC = {2024: 1.00, 2025: 1.05, 2026: 1.13, 2027: 1.20}

contas = [  # codigo, nome, pai, tipo, opex, ordem
    ("3", "Receita Operacional Bruta", "", "GRUPO", "N", 10),
    ("3.1", "Tarifas portuárias", "3", "RECEITA_BRUTA", "N", 11),
    ("3.2", "Arrendamentos", "3", "RECEITA_BRUTA", "N", 12),
    ("3.3", "Outras receitas operacionais", "3", "RECEITA_BRUTA", "N", 13),
    ("4", "Deduções da Receita", "", "GRUPO", "N", 20),
    ("4.1", "PIS/COFINS", "4", "DEDUCAO_RECEITA", "N", 21),
    ("4.2", "ISS", "4", "DEDUCAO_RECEITA", "N", 22),
    ("5", "Custos dos Serviços", "", "GRUPO", "S", 30),
    ("5.1", "Pessoal operacional", "5", "CUSTO", "S", 31),
    ("5.2", "Manutenção de infraestrutura", "5", "CUSTO", "S", 32),
    ("5.3", "Energia elétrica", "5", "CUSTO", "S", 33),
    ("6", "Despesas Operacionais", "", "GRUPO", "S", 40),
    ("6.1", "Pessoal administrativo", "6", "DESPESA_OPERACIONAL", "S", 41),
    ("6.2", "Serviços de terceiros", "6", "GRUPO", "S", 42),
    ("6.2.1", "Vigilância", "6.2", "DESPESA_OPERACIONAL", "S", 43),
    ("6.2.2", "Limpeza e conservação", "6.2", "DESPESA_OPERACIONAL", "S", 44),
    ("6.3", "Seguros", "6", "DESPESA_OPERACIONAL", "S", 45),
    ("6.4", "Despesas gerais", "6", "DESPESA_OPERACIONAL", "S", 46),
    ("7", "Depreciação e Amortização", "", "GRUPO", "N", 50),
    ("7.1", "Depreciação e amortização", "7", "DEPRECIACAO_AMORTIZACAO", "N", 51),
]
# valor mensal base 2024 (R$ mil) e sazonalidade
base_mensal = {"3.1": 30000, "3.2": 8000, "3.3": 1500, "5.1": 6000, "5.2": 3500, "5.3": 1800,
               "6.1": 4000, "6.2.1": 1200, "6.2.2": 600, "6.3": 400, "6.4": 900, "7.1": 2500}
saz = [0.92, 0.90, 0.97, 1.00, 1.04, 1.06, 1.08, 1.07, 1.03, 1.00, 0.97, 0.96]
premissas = {  # codigo: (nome, % por ano)
    "IPCA": ("IPCA acumulado", {2024: "4,50", 2025: "4,80", 2026: "4,50", 2027: "4,00"}),
    "IGPM": ("IGP-M acumulado", {2024: "3,80", 2025: "4,20", 2026: "3,90", 2027: "3,60"}),
    "REAJ_SAL": ("Reajuste salarial (ACT)", {2024: "5,00", 2025: "5,50", 2026: "5,00", 2027: "4,50"}),
    "TARIFA": ("Reajuste tarifário", {2024: "6,00", 2025: "6,00", 2026: "5,50", 2027: "5,00"}),
}
vinculos = [("3.1", "TARIFA"), ("3.2", "IGPM"), ("4.1", "TARIFA"), ("4.2", "TARIFA"),
            ("5.1", "REAJ_SAL"), ("5.2", "IGPM"), ("5.3", "IPCA"), ("6.1", "REAJ_SAL"),
            ("6.2.1", "IPCA"), ("6.2.2", "IPCA"), ("6.3", "IPCA")]
# 3.3, 6.4 e 7.1 ficam sem vínculo de propósito (CT-04).

def br(x):
    return str(Decimal(x).quantize(Decimal("0.01"), ROUND_HALF_UP)).replace(".", ",")

valores = []
for ano in ANOS:
    for mes in range(1, 13):
        orc = {}
        for c, v in base_mensal.items():
            s = saz[mes - 1] if c in ("3.1", "3.3", "5.3") else 1.0
            orc[c] = v * CRESC[ano] * s
        orc["4.1"] = (orc["3.1"] + orc["3.2"] + orc["3.3"]) * 0.0925
        orc["4.2"] = orc["3.1"] * 0.02
        fechado = (ano, mes) <= CORTE
        for c in sorted(orc):
            valores.append((c, ano, mes, "ORCADO", orc[c]))
            if fechado:
                desvio = random.uniform(-0.05, 0.06) if not c.startswith("4") else 0
                real = orc[c] * (1 + desvio)
                valores.append((c, ano, mes, "REALIZADO", real))
        if fechado:  # deduções realizadas acompanham a receita realizada
            rr = {v[0]: v[4] for v in valores if v[1] == ano and v[2] == mes and v[3] == "REALIZADO"}
            for i, v in enumerate(valores):
                if v[1] == ano and v[2] == mes and v[3] == "REALIZADO" and v[0] in ("4.1", "4.2"):
                    novo = (rr["3.1"] + rr["3.2"] + rr["3.3"]) * 0.0925 if v[0] == "4.1" else rr["3.1"] * 0.02
                    valores[i] = (v[0], ano, mes, "REALIZADO", novo)

os.makedirs(destino, exist_ok=True)
w = lambda n, linhas: open(os.path.join(destino, n), "w", encoding="utf-8").write("\n".join(linhas) + "\n")
w("config.csv", ["chave;valor", "data_corte;%04d-%02d" % CORTE, "origem;FICTICIO", "unidade;R$ mil", "versao;1"])
w("contas.csv", ["codigo;nome;codigo_pai;tipo;opex;ordem"] + [";".join(map(str, c)) for c in contas])
w("valores.csv", ["codigo_conta;ano;mes;tipo;valor"] + ["%s;%d;%d;%s;%s" % (c, a, m, t, br(v)) for c, a, m, t, v in valores])
w("premissas.csv", ["codigo;nome;ano;mes;percentual"] + ["%s;%s;%d;%d;%s" % (k, n, a, m, p[a])
    for k, (n, p) in premissas.items() for a in ANOS for m in range(1, 13)])
w("vinculos.csv", ["codigo_conta;codigo_premissa;vigencia_inicio"] + ["%s;%s;2024-01" % v for v in vinculos])
print(len(valores), "valores")
