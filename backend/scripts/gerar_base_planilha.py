#!/usr/bin/env python3
"""
Gera a base fictícia e os arquivos de validação (esperado.csv / Oráculo)
baseados na planilha dadosDeTreinamento.ods do Porto do Itaqui / EMAP.

Esta base modela com 100% de realismo a estrutura contábil, operacional,
sazonalidade e premissas da EMAP para a demonstração executiva.
"""

import sys
import os
import zipfile
import xml.etree.ElementTree as ET
from decimal import Decimal, ROUND_HALF_UP, InvalidOperation

# ---------------------------------------------------------------------------
# Leitura ODS
# ---------------------------------------------------------------------------

NS = {
    'table': 'urn:oasis:names:tc:opendocument:xmlns:table:1.0',
    'text':  'urn:oasis:names:tc:opendocument:xmlns:text:1.0',
    'office':'urn:oasis:names:tc:opendocument:xmlns:office:1.0',
}

def ler_ods(caminho):
    with zipfile.ZipFile(caminho) as z:
        root = ET.fromstring(z.read("content.xml"))
    sheets = root.findall('.//table:table', NS)
    resultado = {}
    for s in sheets:
        nome = s.get('{urn:oasis:names:tc:opendocument:xmlns:table:1.0}name')
        resultado[nome] = _parse_sheet(s)
    return resultado

def _cell_value(cell):
    rep = int(cell.get('{urn:oasis:names:tc:opendocument:xmlns:table:1.0}number-columns-repeated', 1))
    val = cell.get('{urn:oasis:names:tc:opendocument:xmlns:office:1.0}value')
    if val is None:
        texts = []
        for p in cell.findall('.//text:p', NS):
            texts.append(''.join(p.itertext()))
        val = ' '.join(texts).strip()
    return val, min(rep, 60)

def _parse_sheet(sheet, max_cols=50):
    rows = []
    for row in sheet.findall('table:table-row', NS):
        row_rep = int(row.get('{urn:oasis:names:tc:opendocument:xmlns:table:1.0}number-rows-repeated', 1))
        cells = []
        for cell in row.findall('table:table-cell', NS):
            v, r = _cell_value(cell)
            for _ in range(r):
                cells.append(v)
                if len(cells) >= max_cols:
                    break
        while cells and not cells[-1]:
            cells.pop()
        for _ in range(min(row_rep, 2)):
            rows.append(cells[:])
    while rows and not any(rows[-1]):
        rows.pop()
    return rows

# ---------------------------------------------------------------------------
# Estrutura Oficial de Contas (DRE EMAP)
# ---------------------------------------------------------------------------

CONTAS = [
    # 1. Receita Operacional Bruta
    ("1",       "RECEITA OPERACIONAL BRUTA",        None,  "GRUPO",                   "N", 10),
    ("1.1",     "OPERAÇÃO TEGRAM",                  "1",   "GRUPO",                   "N", 11),
    ("1.1.1",   "Movimentação Carga - TEGRAM",       "1.1", "RECEITA_BRUTA",           "N", 12),
    ("1.1.2",   "Arrendamento - TEGRAM",             "1.1", "RECEITA_BRUTA",           "N", 13),
    ("1.1.3",   "Tegram - Diferimento",              "1.1", "RECEITA_BRUTA",           "N", 14),
    ("1.2",     "OPERAÇÃO GRANÉIS LÍQUIDOS",         "1",   "GRUPO",                   "N", 20),
    ("1.2.1",   "Movimentação Carga - Granéis Líquidos","1.2","RECEITA_BRUTA",         "N", 21),
    ("1.2.2",   "Arrendamento - Granéis Líquidos",   "1.2", "RECEITA_BRUTA",           "N", 22),
    ("1.2.3",   "Outorgas - IQI's",                  "1.2", "RECEITA_BRUTA",           "N", 23),
    ("1.3",     "OPERAÇÃO VLI",                      "1",   "GRUPO",                   "N", 30),
    ("1.3.1",   "Movimentação Carga - VLI",          "1.3", "RECEITA_BRUTA",           "N", 31),
    ("1.3.2",   "Arrendamento - VLI",                "1.3", "RECEITA_BRUTA",           "N", 32),
    ("1.4",     "OPERAÇÃO SUZANO",                   "1",   "GRUPO",                   "N", 40),
    ("1.4.1",   "Movimentação Carga - Suzano",       "1.4", "RECEITA_BRUTA",           "N", 41),
    ("1.4.2",   "Arrendamento - Suzano",             "1.4", "RECEITA_BRUTA",           "N", 42),
    ("1.5",     "OPERAÇÃO VALE COBRE",               "1",   "GRUPO",                   "N", 50),
    ("1.5.1",   "Movimentação Carga - VALE Cobre",   "1.5", "RECEITA_BRUTA",           "N", 51),
    ("1.5.2",   "Arrendamento - VALE Cobre",         "1.5", "RECEITA_BRUTA",           "N", 52),
    ("1.6",     "OPERAÇÃO FERTILIZANTES",            "1",   "GRUPO",                   "N", 60),
    ("1.6.1",   "Movimentação Carga - Fertilizantes","1.6", "RECEITA_BRUTA",           "N", 61),
    ("1.6.2",   "Arrendamento - Fertilizantes",      "1.6", "RECEITA_BRUTA",           "N", 62),
    ("1.7",     "OUTROS NEGÓCIOS",                   "1",   "GRUPO",                   "N", 70),
    ("1.7.1",   "Movimentação Carga",                "1.7", "RECEITA_BRUTA",           "N", 71),
    ("1.7.2",   "Armazenagem",                       "1.7", "RECEITA_BRUTA",           "N", 72),
    ("1.7.3",   "Arrendamento",                      "1.7", "RECEITA_BRUTA",           "N", 73),
    ("1.7.4",   "Receitas Complementares",           "1.7", "RECEITA_BRUTA",           "N", 74),
    ("1.7.5",   "Receitas Acessórias",               "1.7", "RECEITA_BRUTA",           "N", 75),
    # 2. Deduções
    ("2",       "DEDUÇÕES À RECEITA OPERACIONAL",    None,  "GRUPO",                   "N", 100),
    ("2.1",     "PIS",                               "2",   "DEDUCAO_RECEITA",         "N", 101),
    ("2.2",     "COFINS",                            "2",   "DEDUCAO_RECEITA",         "N", 102),
    ("2.3",     "ISS",                               "2",   "DEDUCAO_RECEITA",         "N", 103),
    # 3. Custos Operacionais
    ("3",       "CUSTOS",                            None,  "GRUPO",                   "S", 200),
    ("3.1",     "C. Pessoal e Encargos",             "3",   "CUSTO",                   "S", 201),
    ("3.2",     "C. Benefícios à Empregados",        "3",   "CUSTO",                   "S", 202),
    ("3.3",     "C. Serviços de Terceiros - PF",     "3",   "CUSTO",                   "S", 203),
    ("3.4",     "C. Serviços Essenciais",            "3",   "CUSTO",                   "S", 204),
    ("3.5",     "C. Materiais",                      "3",   "CUSTO",                   "S", 205),
    ("3.6",     "C. Serviços Estratégicos",          "3",   "CUSTO",                   "S", 206),
    ("3.7",     "C. Transporte e Locomoção",         "3",   "CUSTO",                   "S", 207),
    ("3.8",     "C. Serviços de Terceiros - PJ",     "3",   "CUSTO",                   "S", 208),
    ("3.9",     "C. Tributárias e Judiciais",        "3",   "CUSTO",                   "S", 209),
    ("3.10",    "C. Infraestrutura Portuária",       "3",   "CUSTO",                   "S", 210),
    # 4. Despesas Administrativas
    ("4",       "DESPESAS",                          None,  "GRUPO",                   "S", 300),
    ("4.1",     "D. Pessoal e Encargos",             "4",   "DESPESA_OPERACIONAL",     "S", 301),
    ("4.2",     "D. Benefícios à Empregados",        "4",   "DESPESA_OPERACIONAL",     "S", 302),
    ("4.3",     "D. Serviços de Terceiros - PF",     "4",   "DESPESA_OPERACIONAL",     "S", 303),
    ("4.4",     "D. Serviços Essenciais",            "4",   "DESPESA_OPERACIONAL",     "S", 304),
    ("4.5",     "D. Perdas e Provisões",             "4",   "DESPESA_OPERACIONAL",     "S", 305),
    ("4.6",     "D. Materiais",                      "4",   "DESPESA_OPERACIONAL",     "S", 306),
    ("4.7",     "D. Serviços Estratégicos",          "4",   "DESPESA_OPERACIONAL",     "S", 307),
    ("4.8",     "D. Transporte e Locomoção",         "4",   "DESPESA_OPERACIONAL",     "S", 308),
    ("4.9",     "D. Serviços de Terceiros - PJ",     "4",   "DESPESA_OPERACIONAL",     "S", 309),
    ("4.10",    "D. Tributárias e Judiciais",        "4",   "DESPESA_OPERACIONAL",     "S", 310),
]

NOME_PARA_COD = {c[1]: c[0] for c in CONTAS}
FOLHAS = {c[0] for c in CONTAS if c[3] != "GRUPO"}

# ---------------------------------------------------------------------------
# Funções Auxiliares
# ---------------------------------------------------------------------------

def fmt(val):
    if val is None:
        return "0,00"
    rounded = Decimal(str(val)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    return str(rounded).replace(".", ",")

def gerar_contas_csv():
    linhas = ["codigo;nome;codigo_pai;tipo;opex;ordem"]
    for cod, nome, pai, tipo, opex, ordem in CONTAS:
        pai_str = pai if pai else ""
        linhas.append(f"{cod};{nome};{pai_str};{tipo};{opex};{ordem}")
    return "\n".join(linhas) + "\n"

def gerar_config_csv(data_corte="2026-09"):
    return (
        "chave;valor\n"
        f"data_corte;{data_corte}\n"
        "origem;FICTICIO\n"
        "unidade;R$ mil\n"
        "versao;2\n"
    )

def gerar_premissas_csv():
    def mensal(acum_anual):
        return round(((1 + acum_anual) ** (1/12) - 1) * 100, 4)

    premissas_def = {
        ("INPC", "INPC"): {
            2024: 0.0483,
            2025: 0.0553,
            2026: 0.0500,
            2027: 0.0421370109369512,
        },
        ("IGPM", "IGP-M"): {
            2024: 0.0369,
            2025: 0.0639,
            2026: 0.0800,
            2027: 0.0395859489196573,
        },
        ("IPCA", "IPCA"): {
            2024: 0.0483,
            2025: 0.0553,
            2026: 0.0483,
            2027: 0.0400617871995308,
        },
    }

    linhas = ["codigo;nome;ano;mes;percentual"]
    for (cod, nome), anos in premissas_def.items():
        for ano, acum in sorted(anos.items()):
            pct_mensal = mensal(acum)
            for mes in range(1, 13):
                linhas.append(f"{cod};{nome};{ano};{mes};{str(pct_mensal).replace('.', ',')}")
    return "\n".join(linhas) + "\n"

def gerar_vinculos_csv():
    vinculos = [
        # Custos
        ("3.1",  "INPC",  "2024-01"),
        ("3.2",  "INPC",  "2024-01"),
        ("3.3",  "IGPM",  "2024-01"),
        ("3.4",  "IGPM",  "2024-01"),
        ("3.5",  "IGPM",  "2024-01"),
        ("3.6",  "IGPM",  "2024-01"),
        ("3.7",  "IGPM",  "2024-01"),
        ("3.8",  "IGPM",  "2024-01"),
        ("3.9",  "IGPM",  "2024-01"),
        ("3.10", "IPCA",  "2024-01"),
        # Despesas
        ("4.1",  "INPC",  "2024-01"),
        ("4.2",  "INPC",  "2024-01"),
        ("4.3",  "IGPM",  "2024-01"),
        ("4.4",  "IGPM",  "2024-01"),
        ("4.6",  "IGPM",  "2024-01"),
        ("4.7",  "IGPM",  "2024-01"),
        ("4.8",  "IGPM",  "2024-01"),
        ("4.9",  "IGPM",  "2024-01"),
        ("4.10", "IGPM",  "2024-01"),
    ]
    linhas = ["codigo_conta;codigo_premissa;vigencia_inicio"]
    for conta, premissa, inicio in vinculos:
        linhas.append(f"{conta};{premissa};{inicio}")
    return "\n".join(linhas) + "\n"

# ---------------------------------------------------------------------------
# Extração e Tratamento Completo dos Valores Mensais (2024..2027)
# ---------------------------------------------------------------------------

def processar_base_dados(sheets):
    DATA_CORTE = (2026, 9)

    orc_anual = sheets.get("Orçado 2024-2027", [])
    orc26 = sheets.get("Orçado 2026", [])
    real25 = sheets.get("Real 2025", [])
    real26 = sheets.get("Real 2026", [])

    # 1. Perfil sazonal e valores de 2026
    pesos_orc = {}
    valores_orc26 = {}
    for r in orc26[2:]:
        if r and r[0] in NOME_PARA_COD:
            cod = NOME_PARA_COD[r[0]]
            if cod in FOLHAS:
                valores_orc26[cod] = {}
                vals = []
                for m, col in enumerate(range(2, 14), 1):
                    v = Decimal('0')
                    if col < len(r) and r[col]:
                        try: v = abs(Decimal(r[col].replace(',', '.')))
                        except: pass
                    valores_orc26[cod][m] = v
                    vals.append(v)
                soma = sum(vals)
                if soma > 0:
                    pesos_orc[cod] = [v / soma for v in vals]
                else:
                    pesos_orc[cod] = [Decimal('1') / Decimal('12')] * 12

    # 2. Totais anuais da aba Orçado 2024-2027
    totais_orc = {}
    for r in orc_anual:
        if r and r[0] in NOME_PARA_COD:
            cod = NOME_PARA_COD[r[0]]
            if cod in FOLHAS:
                totais_orc[cod] = {}
                for idx, a in [(3, 2024), (4, 2025), (5, 2026), (6, 2027)]:
                    if idx < len(r) and r[idx]:
                        try: totais_orc[cod][a] = abs(Decimal(r[idx].replace(',', '.')))
                        except: pass

    # 3. Montar orçado mensal para 2024, 2025, 2026, 2027
    orcado = {} # (ano, mes, cod) -> Decimal
    for cod in FOLHAS:
        # 2026
        if cod in valores_orc26:
            for m, v in valores_orc26[cod].items():
                orcado[(2026, m, cod)] = v
        # 2024, 2025, 2027 distribuídos sazonalmente
        p = pesos_orc.get(cod, [Decimal('1') / Decimal('12')] * 12)
        for a in [2024, 2025, 2027]:
            tot = totais_orc.get(cod, {}).get(a, Decimal('0'))
            if a == 2027 and tot == 0 and cod in valores_orc26:
                # 2027 ainda não tem orçamento preenchido na planilha: projeta sobre 2026 (+5,5% receitas, +4,5% custos/despesas)
                fator = Decimal('1.055') if cod.startswith(('1.', '2.')) else Decimal('1.045')
                for m in range(1, 13):
                    orcado[(2027, m, cod)] = valores_orc26[cod][m] * fator
            else:
                for m in range(1, 13):
                    orcado[(a, m, cod)] = tot * p[m-1]

    # 4. Montar realizado mensal
    realizado = {} # (ano, mes, cod) -> Decimal

    # 2024 e 2025 de Real 2025
    for r in real25[2:]:
        if r and r[0] in NOME_PARA_COD:
            cod = NOME_PARA_COD[r[0]]
            if cod in FOLHAS:
                # 2024: cols 2..13
                for m, col in enumerate(range(2, 14), 1):
                    if col < len(r) and r[col] and r[col] not in ('#VALOR!', '#DIV/0!', ''):
                        try: realizado[(2024, m, cod)] = abs(Decimal(r[col].replace(',', '.')))
                        except: pass
                # 2025: cols 14..25
                for m, col in enumerate(range(14, 26), 1):
                    if col < len(r) and r[col] and r[col] not in ('#VALOR!', '#DIV/0!', ''):
                        try: realizado[(2025, m, cod)] = abs(Decimal(r[col].replace(',', '.')))
                        except: pass

    # 2026 de Real 2026 (meses 1..9, até data de corte)
    for r in real26[2:]:
        if r and r[0] in NOME_PARA_COD:
            cod = NOME_PARA_COD[r[0]]
            if cod in FOLHAS:
                for m, col in enumerate(range(26, 35), 1):
                    if col < len(r) and r[col] and r[col] not in ('#VALOR!', '#DIV/0!', ''):
                        try:
                            v = abs(Decimal(r[col].replace(',', '.')))
                            if v > 0:
                                realizado[(2026, m, cod)] = v
                        except: pass

    return orcado, realizado

def gerar_valores_csv(orcado, realizado):
    linhas = ["codigo_conta;ano;mes;tipo;valor"]
    
    # Orçado
    for (ano, mes, cod), val in sorted(orcado.items()):
        linhas.append(f"{cod};{ano};{mes};ORCADO;{fmt(val)}")
        
    # Realizado (até 2026-09)
    for (ano, mes, cod), val in sorted(realizado.items()):
        if (ano, mes) <= (2026, 9):
            linhas.append(f"{cod};{ano};{mes};REALIZADO;{fmt(val)}")
            
    return "\n".join(linhas) + "\n"

# ---------------------------------------------------------------------------
# Geração do esperado.csv (Oráculo)
# ---------------------------------------------------------------------------

def gerar_esperado_csv(orcado, realizado):
    """
    Calcula com precisão matemática os valores consolidados para o Oráculo
    a partir dos lançamentos das contas folhas.
    """
    contas_tipo = {c[0]: c[3] for c in CONTAS}
    linhas = ["cenario;ano;mes;indicador;perspectiva;valor"]

    def registrar(ano, mes_str, ind, persp, val):
        linhas.append(f"original;{ano};{mes_str};{ind};{persp};{fmt(val)}")

    for ano in [2024, 2025, 2026]:
        for persp, fonte in [("orcado", orcado), ("realizado", realizado)]:
            if ano == 2026 and persp == "realizado":
                meses = range(1, 10) # jan a set
            else:
                meses = range(1, 13)

            tot_rb = Decimal('0')
            tot_ded = Decimal('0')
            tot_custos = Decimal('0')
            tot_desp = Decimal('0')

            for m in meses:
                rb = sum(v for (a, mes, cod), v in fonte.items() if a == ano and mes == m and contas_tipo.get(cod) == 'RECEITA_BRUTA')
                ded = sum(v for (a, mes, cod), v in fonte.items() if a == ano and mes == m and contas_tipo.get(cod) == 'DEDUCAO_RECEITA')
                rl = rb - ded
                custos = sum(v for (a, mes, cod), v in fonte.items() if a == ano and mes == m and contas_tipo.get(cod) == 'CUSTO')
                desp = sum(v for (a, mes, cod), v in fonte.items() if a == ano and mes == m and contas_tipo.get(cod) == 'DESPESA_OPERACIONAL')
                ebitda = rl - custos - desp
                margem = (ebitda / rl * 100) if rl else Decimal('0')

                tot_rb += rb
                tot_ded += ded
                tot_custos += custos
                tot_desp += desp

                # Registrar mensal para 2026
                if ano == 2026:
                    registrar(ano, str(m), "receita_bruta", persp, rb)
                    registrar(ano, str(m), "deducoes", persp, ded)
                    registrar(ano, str(m), "receita_liquida", persp, rl)
                    registrar(ano, str(m), "custos", persp, custos)
                    registrar(ano, str(m), "despesas_operacionais", persp, desp)
                    registrar(ano, str(m), "ebitda", persp, ebitda)
                    registrar(ano, str(m), "margem_ebitda_pct", persp, margem)

            tot_rl = tot_rb - tot_ded
            tot_ebitda = tot_rl - tot_custos - tot_desp
            tot_margem = (tot_ebitda / tot_rl * 100) if tot_rl else Decimal('0')

            registrar(ano, "TOTAL", "receita_bruta", persp, tot_rb)
            registrar(ano, "TOTAL", "deducoes", persp, tot_ded)
            registrar(ano, "TOTAL", "receita_liquida", persp, tot_rl)
            registrar(ano, "TOTAL", "custos", persp, tot_custos)
            registrar(ano, "TOTAL", "despesas_operacionais", persp, tot_desp)
            registrar(ano, "TOTAL", "ebitda", persp, tot_ebitda)
            registrar(ano, "TOTAL", "margem_ebitda_pct", persp, tot_margem)

    return "\n".join(linhas) + "\n"

# ---------------------------------------------------------------------------
# Execução Principal
# ---------------------------------------------------------------------------

def main():
    if len(sys.argv) < 2:
        print("Uso: python3 gerar_base_planilha.py <arquivo.ods> [<dir_base>] [<dir_oraculo>]")
        sys.exit(1)

    ods_path = sys.argv[1]
    simular = "--simular" in sys.argv

    dir_base = sys.argv[2] if len(sys.argv) > 2 and not sys.argv[2].startswith("--") else "src/main/resources/dados"
    dir_oraculo = sys.argv[3] if len(sys.argv) > 3 and not sys.argv[3].startswith("--") else "src/test/resources/oraculo"

    print(f"Lendo planilha {ods_path}...")
    sheets = ler_ods(ods_path)
    print(f"Abas encontradas: {list(sheets.keys())}")

    orcado, realizado = processar_base_dados(sheets)

    arquivos = {
        "config.csv":    gerar_config_csv(),
        "contas.csv":    gerar_contas_csv(),
        "premissas.csv": gerar_premissas_csv(),
        "vinculos.csv":  gerar_vinculos_csv(),
        "valores.csv":   gerar_valores_csv(orcado, realizado),
    }

    esperado = gerar_esperado_csv(orcado, realizado)

    for nome, conteudo in arquivos.items():
        linhas = conteudo.strip().split("\n")
        print(f"  {nome}: {len(linhas)-1} registros gerados")

    print(f"  esperado.csv (Oráculo): {len(esperado.strip().split(chr(10)))-1} registros de validação")

    if not simular:
        os.makedirs(dir_base, exist_ok=True)
        os.makedirs(dir_oraculo, exist_ok=True)
        for nome, conteudo in arquivos.items():
            caminho = os.path.join(dir_base, nome)
            with open(caminho, "w", encoding="utf-8") as f:
                f.write(conteudo)
            print(f"Escrito: {caminho}")
        caminho_esp = os.path.join(dir_oraculo, "esperado.csv")
        with open(caminho_esp, "w", encoding="utf-8") as f:
            f.write(esperado)
        print(f"Escrito: {caminho_esp}")
        print("\nBase de demonstração gerada com sucesso!")
    else:
        print("\nModo simulação: nenhum arquivo gravado.")

if __name__ == "__main__":
    main()
