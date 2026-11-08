import { inject, type InjectionKey } from 'vue';

/** The source of "now" for the calendar. Components never read the system clock; tests provide a fixed one. */
export type NowProvider = () => Date;

export const NOW_KEY: InjectionKey<NowProvider> = Symbol('menura-calendar-now');

/** The only place of the calendar that reads the system clock. */
export const systemNow: NowProvider = () => new Date();

/** The injected "now", or the system clock when nothing is provided. */
export function useNow(): NowProvider {
  return inject(NOW_KEY, systemNow);
}
