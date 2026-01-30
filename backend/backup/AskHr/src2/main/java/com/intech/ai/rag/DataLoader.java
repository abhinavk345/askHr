package com.intech.ai.rag;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

//@Component
public class DataLoader {

    private final VectorStore vectorStore;

    public DataLoader(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

   // @PostConstruct
    public void loadData(){
//in real time we can uplaod data by job , schedular task
        List<String> hrLeavePolicy = List.of(
                "You are an HR assistant for INTECH INDIA.",
                "Your responsibility is to answer employee questions strictly based on the HR Leave Policy provided.",
                "If a question is outside this policy, politely respond that the information is not available.",

                "NEED BASED LEAVE (NBL): Maximum entitlement is 2 Need Based Leaves per quarter on a pro-rata basis.",
                "NBL allocation at joining: First month of quarter gets 2 NBL, second month gets 1 NBL, third month gets 0 NBL.",
                "At the time of relieving: First month gives 0 NBL, second month gives 1 NBL, third month gives 2 NBL.",
                "Excess NBL utilization beyond allowable limits will be deducted from Full & Final Settlement.",
                "Any excess NBL usage beyond quarterly balance is treated as Leave Without Pay.",
                "Employees on probation are eligible for NBL on a pro-rata basis only.",

                "NBL eligibility rule: Reason for leave must be mentioned while applying.",
                "Medical certificate is mandatory for sickness or medical leave exceeding 2 days under NBL.",
                "If all NBLs are exhausted, short or unplanned leaves are treated as Leave Without Pay.",
                "Fresh NBL eligibility starts from the next leave calendar year.",

                "Prior approval is not required for NBL.",
                "Unused NBL is carried forward to the next quarter.",
                "A maximum of 2 unused NBLs are encashed if not utilized by the next quarter.",
                "NBL encashment is calculated based on the last earned Basic Salary.",

                "NBL should not be clubbed with weekends, public holidays, or privilege leave.",
                "If NBL is taken on both sides of a weekend or holiday, the weekend or holiday is also counted as NBL except in medical emergencies.",

                "NBL must be applied via the iON Leave Module.",
                "NBL must be applied within 2 working days after resuming duty, otherwise it will be treated as Leave Without Pay.",

                "LEAVE WITHOUT PAY (LWP): Applicable when all other leaves are exhausted.",
                "LWP requires approval from the reporting manager.",
                "If LWP is taken for the entire month, all days including weekends and holidays are treated as LWP.",

                "MATERNITY LEAVE is governed by the Maternity Benefit Act, 1961.",
                "Eligibility for maternity leave requires a minimum of 80 days of service in the last 12 months.",
                "Maternity leave entitlement is 26 weeks for the first two children.",
                "Maternity leave entitlement is 12 weeks for more than two children.",
                "Maternity leave can start up to 8 weeks before delivery or 6 weeks if more than two children.",
                "Full salary is paid during maternity leave.",
                "Maternity leave cannot be clubbed with Need Based Leave.",
                "Miscarriage entitlement under maternity leave is 6 weeks.",
                "Unused maternity leave lapses at the end of the calendar year.",

                "PATERNITY LEAVE eligibility requires a minimum of 80 days of service.",
                "Paternity leave entitlement is 5 paid days.",
                "Paternity leave can be availed only twice during service.",
                "Paternity leave is applicable only for the first two children.",
                "Paternity leave must be taken in one continuous stretch within 3 months of childbirth.",

                "COMPENSATORY OFF is applicable when working on weekends or public holidays.",
                "Working more than 4 hours earns a half-day Comp Off.",
                "Working more than 6 hours earns a full-day Comp Off.",
                "Comp Off is valid till the end of the next quarter.",
                "Working on mandatory holidays such as 26 January, 15 August, and 2 October earns 2 Comp Offs.",
                "Unused Comp Offs are encashed based on Gross Salary.",

                "PUBLIC HOLIDAYS are fixed and announced annually by HR.",
                "The holiday calendar follows from January 1st to December 31st.",

                "BIRTHDAY LEAVE provides one paid leave per calendar year.",
                "Birthday leave must be taken on the actual birthday.",
                "If the birthday falls on a weekend or public holiday, the birthday leave lapses.",
                "Birthday leave cannot be accumulated or encashed.",

                "BEREAVEMENT LEAVE is applicable to all regular full-time employees.",
                "Bereavement leave entitlement is 5 consecutive paid days per occurrence.",
                "Bereavement leave applies to immediate family members only.",
                "Bereavement leave cannot be accumulated or encashed.",
                "Bereavement leave must be approved and may require supporting documentation.",

                "PROLONGED MEDICAL LEAVE requires employees to first utilize all available leaves.",
                "Privilege Leave and Need Based Leave can be clubbed for prolonged medical leave.",
                "Any additional leave beyond available balance is treated as Leave Without Pay.",
                "Maximum Leave Without Pay allowed under prolonged medical leave is 60 days.",

                "GENERAL RULE: All leaves must be applied via the iON Leave Module.",
                "In emergencies, employees must inform the manager immediately and apply later.",
                "Any leave taken without approval will be treated as Leave Without Pay.");

            List<Document> documentList = hrLeavePolicy.stream().map(Document::new).collect(Collectors.toList());
            vectorStore.add(documentList);
    }
}
