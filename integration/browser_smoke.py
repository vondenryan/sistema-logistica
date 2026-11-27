"""Check navigation, authentication and reload in a real browser."""
import os
from playwright.sync_api import expect, sync_playwright

base = os.environ["P2_SMOKE_URL"]
password = os.environ["P2_SMOKE_PASSWORD"]


def login(page, account):
    page.get_by_label("E-mail", exact=True).fill(f"sprint4-{account}@example.invalid")
    page.get_by_label("Senha", exact=True).fill(password)
    page.get_by_role("button", name="Entrar").click()


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(executable_path=os.environ.get("CHROMIUM_PATH"))
    page = browser.new_page()
    page.goto(base)
    page.get_by_role("link", name="Pedidos, frete e rotas").click()
    login(page, "operator")
    expect(page.get_by_role("heading", name="Pedidos", exact=True)).to_be_visible()
    page.reload()
    expect(page.get_by_role("heading", name="Pedidos", exact=True)).to_be_visible()
    page.get_by_role("link", name="Módulos", exact=True).click()
    page.get_by_role("link", name="Parceiros e frota").click()
    login(page, "operator")
    page.get_by_role("link", name="Veículos", exact=True).click()
    page.reload()
    expect(page.get_by_role("heading", name="Veículos", exact=True)).to_be_visible()
    expect(page).to_have_url(base + "/frota/vehicles")
    page.get_by_role("button", name="Sair", exact=True).click()
    login(page, "other-store")
    expect(page).to_have_url(base + "/frota/stores")
    expect(page.get_by_role("link", name="Veículos", exact=True)).to_have_count(0)
    page.get_by_role("link", name="Módulos", exact=True).click()
    page.get_by_role("link", name="Rastreamento público", exact=True).click()
    expect(page.get_by_label("Código de rastreamento")).to_be_visible()
    page.reload()
    expect(page.get_by_label("Código de rastreamento")).to_be_visible()
    page.get_by_role("button", name="Painel do Motorista").click()
    login(page, "journey-driver")
    expect(page.get_by_role("heading", name="Minhas Entregas", exact=False)).to_be_visible()
    page.reload()
    expect(page.get_by_role("heading", name="Minhas Entregas", exact=False)).to_be_visible()
    browser.close()
print("Chromium verificou navegação, login, permissões da loja e recarga dos três módulos.")
