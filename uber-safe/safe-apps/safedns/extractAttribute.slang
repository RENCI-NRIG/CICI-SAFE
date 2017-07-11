resolvePredicateAttr(?Speaker, ?SpeakerSetRef, ?SubjectCN, ?SubjectSetRef) :-
  spec('
    find the srn predicate from the SetRef context and extract 
    the set reference for subsequent fetch
  '),
  (* lsetToSeq? converts the slog results to a slang sequence *)
  ?AttrSeq := lsetToSeq?(queryPredicate?(?SpeakerSetRef, ?Speaker, ?SubjectCN)),
  getAttribute(?AttrSeq, ?Speaker, ?SubjectSetRef, ?SubjectCN),
end

 
getAttribute([?Head|?Tail], ?Speaker, ?SubjectSetRef, ?SubjectCN) :-
  spec('
    extract the set reference attribute value from the 
    first result of slog output sequence
  '),
  ?Head = srn(?Speaker, ?SubjectSetRef, ?SubjectCN),
end

defguard queryPredicate?(?SetRef, ?Speaker, ?SubjectCN) :-
  spec('query the required predicate from the set context'),
  _ := fetch?(?SetRef),
  {
    import!($SetRef).
    $Speaker: srn(?SubjectSetRef, $SubjectCN)?
  },
end
