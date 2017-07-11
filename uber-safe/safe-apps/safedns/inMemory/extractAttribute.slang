resolvePredicateAttr(?SetRef, ?Speaker, ?Subject, ?SubjectSetRef) :-
  spec('
    find the srn predicate from the SetRef context and extract 
    the set reference for subsequent fetch
  '),
  (* lsetToSeq? converts the slog results to a slang sequence *)
  ?AttrSeq := lsetToSeq?(queryPredicate?(?SetRef, ?Speaker, ?Subject)),
  getAttribute(?AttrSeq, ?Speaker, ?Subject, ?SubjectSetRef),
end
  
getAttribute([?Head|?Tail], ?Speaker, ?Subject, ?SubjectSetRef) :-
  spec('
    extract the set reference attribute value from the 
    first result of slog output sequence
  '),
  ?Head = srn(?Speaker, ?Subject, ?SubjectSetRef),
end

defguard queryPredicate?(?SetRef, ?Speaker, ?Subject) :-
  spec('query the required predicate from the set context'),
  {
    import!($SetRef).
    $Speaker: srn($Subject, ?SubjectSetRef)?
  },
end
