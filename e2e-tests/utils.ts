import { Page, Locator } from 'playwright'
import { expect } from '@playwright/test'
import * as process from 'process'
import { Block } from './types'

export const IsMac = process.platform === 'darwin'
export const IsLinux = process.platform === 'linux'
export const IsWindows = process.platform === 'win32'

export function randomString(length: number) {
  const characters = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';

  let result = '';
  const charactersLength = characters.length;
  for (let i = 0; i < length; i++) {
    result += characters.charAt(Math.floor(Math.random() * charactersLength));
  }

  return result;
}

export async function createRandomPage(page: Page) {
  const randomTitle = randomString(20)

  // Click #search-button
  await page.click('#search-button')
  // Fill [placeholder="Search or create page"]
  await page.fill('[placeholder="Search or create page"]', randomTitle)
  // Click text=/.*New page: "new page".*/
  await page.click('text=/.*New page: ".*/')
  // wait for textarea of first block
  await page.waitForSelector('textarea >> nth=0', { state: 'visible' })

  return randomTitle;
}

export async function createPage(page: Page, page_name: string) {// Click #search-button
  await page.click('#search-button')
  // Fill [placeholder="Search or create page"]
  await page.fill('[placeholder="Search or create page"]', page_name)
  // Click text=/.*New page: "new page".*/
  await page.click('text=/.*New page: ".*/')
  // wait for textarea of first block
  await page.waitForSelector('textarea >> nth=0', { state: 'visible' })

  return page_name;
}


export async function searchAndJumpToPage(page: Page, pageTitle: string) {
  await page.click('#search-button')
  await page.fill('[placeholder="Search or create page"]', pageTitle)
  await page.waitForSelector(`[data-page-ref="${pageTitle}"]`, { state: 'visible' })
  await page.click(`[data-page-ref="${pageTitle}"]`)
  return pageTitle;
}

/**
* Locate the last block in the inner editor
* @param page The Playwright Page object.
* @returns The locator of the last block.
*/
export async function lastBlock(page: Page): Promise<Locator> {
  // discard any popups
  await page.keyboard.press('Escape')
  // click last block
  if (await page.locator('text="Click here to edit..."').isVisible()) {
    await page.click('text="Click here to edit..."')
  } else {
    await page.click('.page-blocks-inner .ls-block >> nth=-1')
  }
  // wait for textarea
  await page.waitForSelector('textarea >> nth=0', { state: 'visible' })
  await page.waitForTimeout(100)
  return page.locator('textarea >> nth=0')
}

/**
 * Press Enter and create the next block.
 * @param page The Playwright Page object.
 */
export async function enterNextBlock(page: Page): Promise<Locator> {
  let blockCount = await page.locator('.page-blocks-inner .ls-block').count()
  await page.press('textarea >> nth=0', 'Enter')
  await page.waitForTimeout(10)
  await page.waitForSelector(`.ls-block >> nth=${blockCount} >> textarea`, { state: 'visible' })
  return page.locator('textarea >> nth=0')
}

/**
* Create and locate a new block at the end of the inner editor
* @param page The Playwright Page object
* @returns The locator of the last block
*/
export async function newInnerBlock(page: Page): Promise<Locator> {
  await lastBlock(page)
  await page.press('textarea >> nth=0', 'Enter')

  return page.locator('textarea >> nth=0')
}

export async function newBlock(page: Page): Promise<Locator> {
  let blockNumber = await page.locator('.page-blocks-inner .ls-block').count()
  const prev = await lastBlock(page)
  await page.press('textarea >> nth=0', 'Enter')
  await page.waitForSelector(`.page-blocks-inner .ls-block >> nth=${blockNumber} >> textarea`, { state: 'visible' })
  return page.locator('textarea >> nth=0')
}

export async function escapeToCodeEditor(page: Page): Promise<void> {
  await page.press('.block-editor textarea', 'Escape')
  await page.waitForSelector('.CodeMirror pre', { state: 'visible' })

  await page.waitForTimeout(300)
  await page.click('.CodeMirror pre')
  await page.waitForTimeout(300)

  await page.waitForSelector('.CodeMirror textarea', { state: 'visible' })
}

export async function escapeToBlockEditor(page: Page): Promise<void> {
  await page.waitForTimeout(300)
  await page.click('.CodeMirror pre')
  await page.waitForTimeout(300)

  await page.press('.CodeMirror textarea', 'Escape')
  await page.waitForTimeout(300)
}

export async function setMockedOpenDirPath(
  page: Page,
  path?: string
): Promise<void> {
  // set next open directory
  await page.evaluate(
    ([path]) => {
      Object.assign(window, {
        __MOCKED_OPEN_DIR_PATH__: path,
      })
    },
    [path]
  )
}

export async function openLeftSidebar(page: Page): Promise<void> {
  let sidebar = page.locator('#left-sidebar')

  // Left sidebar is toggled by `is-open` class
  if (!/is-open/.test(await sidebar.getAttribute('class'))) {
    await page.click('#left-menu.button')
    await page.waitForTimeout(10)
    await expect(sidebar).toHaveClass(/is-open/)
  }
}

export async function loadLocalGraph(page: Page, path?: string): Promise<void> {
  await setMockedOpenDirPath(page, path);

  const onboardingOpenButton = page.locator('strong:has-text("Choose a folder")')

  if (await onboardingOpenButton.isVisible()) {
    await onboardingOpenButton.click()
  } else {
    await page.click('#left-menu.button')
    let sidebar = page.locator('#left-sidebar')
    if (!/is-open/.test(await sidebar.getAttribute('class'))) {
      await page.click('#left-menu.button')
      await expect(sidebar).toHaveClass(/is-open/)
    }

    await page.click('#left-sidebar #repo-switch');
    await page.waitForSelector('#left-sidebar .dropdown-wrapper >> text="Add new graph"',
      { state: 'visible', timeout: 5000 })

    await page.click('text=Add new graph')
    await page.waitForSelector('strong:has-text("Choose a folder")',
      { state: 'visible', timeout: 5000 })
    await page.click('strong:has-text("Choose a folder")')

    const skip = page.locator('a:has-text("Skip")')
    await skip.click()
  }

  setMockedOpenDirPath(page, ''); // reset it

  await page.waitForSelector(':has-text("Parsing files")', {
    state: 'hidden',
    timeout: 1000 * 60 * 5,
  })

  const title = await page.title()
  if (title === "Import data into Logseq" || title === "Add another repo") {
    await page.click('a.button >> text=Skip')
  }

  await page.waitForFunction('window.document.title === "Logseq"')

  console.log('Graph loaded for ' + path)
}

export async function activateNewPage(page: Page) {
  await page.click('.ls-block >> nth=0')
  await page.waitForTimeout(500)
}

export async function editFirstBlock(page: Page) {
  await page.click('.ls-block .block-content >> nth=0')
}

export function randomInt(min: number, max: number): number {
  return Math.floor(Math.random() * (max - min + 1) + min)
}

export function randomBoolean(): boolean {
  return Math.random() < 0.5;
}
