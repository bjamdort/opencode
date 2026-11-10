import { Button } from "@opencode/ui/button"
import { Show } from "solid-js"
import { useLanguage } from "@/runtime/i18n/language"

export function AndroidNotifications() {
  const language = useLanguage()

  return (
    <Show when={import.meta.env.MODE === "android"}>
      <Button variant="outline" onClick={() => window.location.assign("opencode-notifications:settings")}>
        {language.t("settings.notifications.android.title")}
      </Button>
    </Show>
  )
}
