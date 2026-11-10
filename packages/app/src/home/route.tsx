import { ScrollView } from "@opencode/ui/scroll-view"
import { Button } from "@opencode/ui/button"
import { useDialog } from "@opencode/ui/context/dialog"
import { createMediaQuery } from "@solid-primitives/media"
import { Show } from "solid-js"
import { useLanguage } from "@/runtime/i18n/language"
import { DialogServer } from "@/servers/connect/dialog"
import { useSettingsDialog } from "@/settings/command"
import { AndroidNotifications } from "@/settings/notifications/android"
import { createHomeController } from "./model"
import { createHomeProjectsController } from "./projects/controller"
import { HomeUtilityNav } from "./projects/view"
import { HomeProjects } from "./projects/region"
import { createHomeScrollController } from "./scroll"
import { createHomeSessionSearchController } from "./sessions/search"
import { createHomeSessionsController } from "./sessions/controller"
import { HomeSessions } from "./sessions/region"

export function Home() {
  const mobile = createMediaQuery("(max-width: 767px)")
  const home = createHomeController()
  const projects = createHomeProjectsController(home)
  const sessions = createHomeSessionsController(home)
  const search = createHomeSessionSearchController(home, sessions)
  const scroll = createHomeScrollController(sessions.data.groups)

  return (
    <div
      data-slot="home-panel"
      class={`
        mx-[var(--shell-inline-inset,8px)] mb-[var(--shell-bottom-inset,8px)] mt-[var(--shell-top-inset,8px)] flex min-h-0 flex-1 flex-col self-stretch overflow-hidden rounded-[10px]
        bg-v2-background-bg-base shadow-[var(--v2-elevation-raised)]
      `}
    >
      <Show when={mobile()}>
        <div class="relative z-40 -mb-3 shrink-0 px-3 pt-3">
          <HomeProjects projects={projects} scroll={scroll} dropdown />
        </div>
      </Show>
      <ScrollView
        class="min-h-0 flex-1 [container-type:size]"
        thumbContainer={scroll.viewport.thumbTrack()}
        thumbHoverTarget={scroll.viewport.hoverTarget()}
        viewportRef={scroll.viewport.setViewport}
        onScroll={(event) => scroll.viewport.update(event.currentTarget.scrollTop)}
      >
        <div
          class={`
            mx-auto grid min-h-full w-full max-w-[1080px] grid-rows-[auto_minmax(0,1fr)] gap-4 px-3
            max-md:grid-rows-[minmax(0,1fr)] lg:grid-cols-[280px_minmax(0,720px)] lg:grid-rows-1 lg:gap-8 lg:px-6
          `}
        >
          <Show when={!mobile()}>
            <HomeProjects projects={projects} scroll={scroll} />
          </Show>
          <Show
            when={import.meta.env.MODE === "android" && home.server.list().length === 0}
            fallback={<HomeSessions sessions={sessions} search={search} scroll={scroll} />}
          >
            <OfflineHome />
          </Show>
        </div>
      </ScrollView>
      <div class="hidden shrink-0 px-3 py-2 md:block lg:hidden">
        <HomeUtilityNav
          class="flex"
          onOpenSettings={projects.utility.settings}
          onOpenHelp={projects.utility.help}
          language={projects.copy.language}
        />
      </div>
    </div>
  )
}

function OfflineHome() {
  const language = useLanguage()
  const dialog = useDialog()
  const settings = useSettingsDialog()

  return (
    <div class="flex min-h-0 flex-1 flex-col items-center justify-center gap-4 px-6 py-12 text-center">
      <h1 class="text-16-medium text-v2-text-text-base">{language.t("home.offline.title")}</h1>
      <p class="max-w-sm text-v2-text-text-muted">{language.t("home.offline.description")}</p>
      <div class="flex flex-wrap justify-center gap-2">
        <Button onClick={() => dialog.show(() => <DialogServer mode="add" />)}>
          {language.t("dialog.server.add.button")}
        </Button>
        <Button variant="outline" onClick={settings}>
          {language.t("command.settings.open")}
        </Button>
        <AndroidNotifications />
      </div>
    </div>
  )
}
