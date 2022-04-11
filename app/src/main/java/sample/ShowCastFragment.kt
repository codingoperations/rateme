package sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import io.least.case_management.ui.cases.CaseListFragment
import io.least.case_management.viewmodel.CaseListConfig
import io.least.collector.DeviceDataCollector
import io.least.connector.RestConnector
import io.least.data.LabelValue
import io.least.data.RateExperienceConfig
import io.least.data.RateExperienceServerConfig
import io.least.data.Tag
import io.least.ui.app.RateAppFragment
import io.least.ui.app.RatemeExpActivity
import io.least.ui.experience.RateExperienceFragment
import io.least.viewmodel.RateMeConfig
import io.sample.R
import io.sample.databinding.FragmentSampleShowcaseBinding

class ShowCastFragment : Fragment() {

    private var _binding: FragmentSampleShowcaseBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSampleShowcaseBinding.inflate(inflater, container, false)
        binding.buttonRateApp.setOnClickListener {
            RateAppFragment.show(
                parentFragmentManager,
                requireActivity().classLoader,
                RestConnector(DeviceDataCollector(), ""),
                RateMeConfig(minPositiveRate = 3.0f)
            )
        }
        
        binding.buttonRateExperience.setOnClickListener {
            RatemeExpActivity.startActivity(requireActivity())
        }
        binding.buttonCaseManagement.setOnClickListener {
            CaseListFragment.show(
                parentFragmentManager,
                R.id.fragmentContainer,
                requireActivity().classLoader,
                CaseListConfig("https://example.com/v1/case-list", "profile-id-beka")
            )
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}