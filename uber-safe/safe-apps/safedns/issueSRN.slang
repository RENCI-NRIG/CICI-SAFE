
defenv Selfie :-
  spec('Load the principal keypair'),
  principal?($1),
end

defcon issueSRN?(?Speaker, ?Subject, ?SubjectCN) :-
  spec('Issue a srn for the Subject with common name as SubjectCN'),
  "$SubjectCN"{
    $Speaker: srn($Subject, $SubjectCN).
  },
end

definit post?(issueSRN?($Self, $2, $3)).
