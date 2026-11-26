import { createScreenRequest } from "./screenRequest";

export interface ScreenPeriod {
  startMonth: string;
  endMonth: string;
}
const periods = new Map<string, ScreenPeriod>();
const channels = new Map<
  string,
  ReturnType<typeof createScreenRequest<ScreenPeriod>>
>();
export function screenPeriodChannel(screenId: string) {
  let channel = channels.get(screenId);
  if (!channel) {
    channel = createScreenRequest<ScreenPeriod>(screenId);
    channels.set(screenId, channel);
  }
  return channel;
}
export function publishScreenPeriod(
  screenId: string,
  period: ScreenPeriod | undefined,
) {
  if (period) periods.set(screenId, period);
  else periods.delete(screenId);
}
export const currentScreenPeriod = (screenId: string) => periods.get(screenId);
