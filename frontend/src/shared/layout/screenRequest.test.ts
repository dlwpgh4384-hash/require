import { expect, it } from "vitest";
import { createScreenRequest } from "./screenRequest";

it("받는 화면이 없으면 마지막 요청을 남겼다가 처음 구독할 때 한 번만 넘긴다", () => {
  const channel = createScreenRequest<string>("UI-01");
  channel.request("P-1");
  channel.request("P-2");
  const got: string[] = [];
  const stop = channel.subscribe((value) => got.push(value));
  expect(got).toEqual(["P-2"]);
  stop();
  const again: string[] = [];
  channel.subscribe((value) => again.push(value))();
  expect(again).toEqual([]);
});

it("받는 화면이 열려 있으면 바로 넘기고 남기지 않는다", () => {
  const channel = createScreenRequest<string>("UI-01");
  const got: string[] = [];
  const stop = channel.subscribe((value) => got.push(value));
  channel.request("P-3");
  stop();
  expect(got).toEqual(["P-3"]);
  const later: string[] = [];
  channel.subscribe((value) => later.push(value))();
  expect(later).toEqual([]);
});
