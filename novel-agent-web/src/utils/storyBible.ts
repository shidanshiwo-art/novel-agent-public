import type {
  SaveStoryBibleRequest,
  StoryBibleDraft,
  StoryBibleResponse,
} from '../types'

export interface StoryBibleForm extends SaveStoryBibleRequest {
  powerName: string
  powerDescription: string
  powerLevels: string
  powerSupplement: string
  hardRules: string[]
}

export function createEmptyStoryBibleForm(): StoryBibleForm {
  return {
    oneSentencePremise: '',
    coreTheme: '',
    mainConflict: '',
    endingDirection: '',
    worldBackground: '',
    powerSystemJson: '',
    hardRulesJson: '',
    styleGuide: '',
    powerName: '',
    powerDescription: '',
    powerLevels: '',
    powerSupplement: '',
    hardRules: [''],
  }
}

function stringValue(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

function stringList(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((line): line is string => typeof line === 'string' && Boolean(line.trim()))
    : []
}

function editableList(lines: string[]): string[] {
  return lines.length ? lines : ['']
}

function textValue(value: unknown): string {
  if (typeof value === 'string') return value
  if (Array.isArray(value)) return stringList(value).join('\n')
  return ''
}

function parsePowerSystem(value: string | null | undefined) {
  const empty = { name: '', description: '', levels: '', supplement: '' }
  if (!value) return empty
  try {
    const parsed: unknown = JSON.parse(value)
    if (Array.isArray(parsed)) return { ...empty, levels: stringList(parsed).join('\n') }
    if (parsed && typeof parsed === 'object') {
      const power = parsed as Record<string, unknown>
      return {
        name: stringValue(power.name) || stringValue(power.名称),
        description: stringValue(power.description)
          || stringValue(power.说明)
          || stringValue(power.mechanism)
          || stringValue(power.机制)
          || stringValue(power.source),
        levels: textValue(power.levels)
          || textValue(power.level)
          || textValue(power.等级)
          || textValue(power.境界)
          || textValue(power.层级)
          || textValue(power.ranks),
        supplement: stringValue(power.supplement)
          || stringValue(power.补充设定)
          || stringValue(power.cost)
          || stringValue(power.代价)
          || stringValue(power.limit)
          || stringValue(power.限制),
      }
    }
  } catch {
    // Legacy plain text is presented as a readable system description.
  }
  return { ...empty, description: value }
}

function parseHardRules(value: string | null | undefined): string[] {
  if (!value) return ['']
  try {
    const parsed: unknown = JSON.parse(value)
    if (Array.isArray(parsed)) return editableList(stringList(parsed))
    if (parsed && typeof parsed === 'object') {
      const rules = stringList((parsed as Record<string, unknown>).rules)
      if (rules.length) return rules
    }
  } catch {
    // Legacy plain text is split into editable rule rows.
  }
  return editableList(value.split(/\r?\n/).map((line) => line.trim()).filter(Boolean))
}

export function storyBibleToForm(bible: StoryBibleResponse | null | undefined): StoryBibleForm {
  const form = createEmptyStoryBibleForm()
  if (!bible) return form
  const power = parsePowerSystem(bible.powerSystemJson)
  return {
    ...form,
    ...bible,
    powerName: power.name,
    powerDescription: power.description,
    powerLevels: power.levels,
    powerSupplement: power.supplement,
    hardRules: parseHardRules(bible.hardRulesJson),
  }
}

export function storyBibleFormToRequest(form: StoryBibleForm): SaveStoryBibleRequest {
  return {
    oneSentencePremise: form.oneSentencePremise,
    coreTheme: form.coreTheme,
    mainConflict: form.mainConflict,
    endingDirection: form.endingDirection,
    worldBackground: form.worldBackground,
    styleGuide: form.styleGuide,
    powerSystemJson: JSON.stringify({
      name: form.powerName.trim(),
      description: form.powerDescription.trim(),
      levels: form.powerLevels.trim(),
      supplement: form.powerSupplement.trim(),
    }),
    hardRulesJson: JSON.stringify(cleanList(form.hardRules)),
    status: form.status,
  }
}

export function storyBibleDraftToForm(draft: StoryBibleDraft | null | undefined): StoryBibleForm {
  const form = createEmptyStoryBibleForm()
  if (!draft) return form
  const power = draft.powerSystem
  const legacyPower = power as unknown as Record<string, unknown> | null | undefined
  return {
    ...form,
    oneSentencePremise: stringValue(draft.oneSentencePremise),
    coreTheme: stringValue(draft.coreTheme),
    mainConflict: stringValue(draft.mainConflict),
    endingDirection: stringValue(draft.endingDirection),
    worldBackground: stringValue(draft.worldBackground),
    styleGuide: stringValue(draft.styleGuide),
    powerName: stringValue(power?.name),
    powerDescription: stringValue(power?.description)
      || stringValue(legacyPower?.mechanism)
      || stringValue(legacyPower?.source),
    powerLevels: textValue(power?.levels)
      || textValue(legacyPower?.levels)
      || textValue(legacyPower?.ranks),
    powerSupplement: stringValue(power?.supplement)
      || stringValue(legacyPower?.cost)
      || stringValue(legacyPower?.limit),
    hardRules: editableList(stringList(draft.hardRules)),
    status: undefined,
  }
}

export function storyBibleFormToDraft(form: StoryBibleForm): StoryBibleDraft {
  // 体系说明与补充设定保持为开放文本，承载题材特有概念而不拆成固定字段。
  const powerSystem = {
    name: form.powerName.trim(),
    description: form.powerDescription.trim(),
    levels: form.powerLevels.trim(),
    supplement: form.powerSupplement.trim(),
  }
  return {
    oneSentencePremise: form.oneSentencePremise,
    coreTheme: form.coreTheme,
    mainConflict: form.mainConflict,
    endingDirection: form.endingDirection,
    worldBackground: form.worldBackground,
    powerSystem: powerSystem.name || powerSystem.description || powerSystem.levels || powerSystem.supplement
      ? powerSystem : null,
    hardRules: cleanList(form.hardRules),
    styleGuide: form.styleGuide,
  }
}

function cleanList(values: string[]): string[] {
  return values.map((value) => value.trim()).filter(Boolean)
}
