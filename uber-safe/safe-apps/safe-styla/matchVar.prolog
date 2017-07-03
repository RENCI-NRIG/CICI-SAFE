'GtgbWydEFxa4zAUnPmLyIT1RznS7U8nAglrHm_XyHhk': delegateMember('z8Gz4FHIQjdctmI3SmKAg39qn_Lel60KI2FX3YFmhn0','532jAHbSMsL03My8pSP25ho8eodnl8gXv-1OlHCmIcA:project1',true).

member(?User, ?Project, ?Delegatable) :-
  ?Delegator: delegateMember(?User, ?Project, ?Delegatable),
  member(?Delegator, ?Project, true).

member('GtgbWydEFxa4zAUnPmLyIT1RznS7U8nAglrHm_XyHhk', '532jAHbSMsL03My8pSP25ho8eodnl8gXv-1OlHCmIcA:project1', true).
