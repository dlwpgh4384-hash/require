/** 서버에 닿지 못했을 때(네트워크 끊김 등) 브라우저 영어 문구 「Failed to fetch」 대신 쓰는 우리말(2026-11-24 사용자 결정). */
export const NETWORK_MESSAGE = "서버에 연결하지 못했습니다. 네트워크를 확인하고 다시 시도해 주세요.";

/**
 * fetch가 네트워크 오류(TypeError)로 실패하면 같은 TypeError를 우리말 문구로 다시 던진다.
 * 요청 취소(AbortError)와 서버 응답(4xx·5xx)은 그대로 둔다. 앱 시작 때 한 번만 건다.
 */
export function wrapNetworkErrors(target: { fetch: typeof fetch }) {
  const original = target.fetch.bind(target);
  target.fetch = (async (...args: Parameters<typeof fetch>) => {
    try {
      return await original(...args);
    } catch (failure) {
      if (failure instanceof TypeError) throw new TypeError(NETWORK_MESSAGE);
      throw failure;
    }
  }) as typeof fetch;
}
